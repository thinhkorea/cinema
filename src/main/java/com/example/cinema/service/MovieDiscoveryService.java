package com.example.cinema.service;

import com.example.cinema.domain.Movie;
import com.example.cinema.dto.MovieDiscoveryResultDTO;
import com.example.cinema.repository.MovieRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class MovieDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(MovieDiscoveryService.class);

    private static final int DEFAULT_LIMIT = 1;
    private static final int MAX_LIMIT = 12;
    private static final int DEFAULT_RERANK_CANDIDATE_LIMIT = 12;
    private static final int MIN_RERANK_CANDIDATE_LIMIT = 5;
    private static final int MAX_RERANK_CANDIDATE_LIMIT = 24;
    private static final int MAX_QUERY_PHRASE_SIZE = 6;
    private static final double MIN_SCORE = 5.0;
    private static final double RERANK_SCORE_CONFIDENCE_START = 0.02;
    private static final double RERANK_SCORE_CONFIDENCE_FULL = 0.20;
    private static final double RERANK_RANGE_CONFIDENCE_START = 0.015;
    private static final double RERANK_RANGE_CONFIDENCE_FULL = 0.12;
    private static final double LOW_CONFIDENCE_RERANK_MAX_SCORE = 0.25;
    private static final double TRUSTED_RERANK_SCORE_FULL = 0.55;
    private static final double RERANK_RELATIVE_RANGE_EPSILON = 0.000001;
    private static final double SHORT_QUERY_DENSE_EVIDENCE_WEIGHT = 0.15;
    private static final double LONG_QUERY_DENSE_EVIDENCE_WEIGHT = 0.60;
    private static final int DENSE_EVIDENCE_LONG_QUERY_START_TOKENS = 12;
    private static final int DENSE_EVIDENCE_LONG_QUERY_FULL_TOKENS = 28;
    private static final double LOW_CONFIDENCE_RERANK_TIE_BREAK_MAX_SCORE = 0.02;
    private static final double RERANK_TIE_BREAK_WEIGHT_WITH_DENSE = 0.02;
    private static final double RERANK_TIE_BREAK_WEIGHT_WITHOUT_DENSE = 0.04;
    private static final double RERANK_NEUTRAL_PERCENT = 50.0;
    private static final double EXACT_TITLE_QUERY_SCORE = 95.0;
    private static final Pattern NON_WORD_PATTERN = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "toi", "minh", "em", "anh", "chi", "ban", "be", "nguoi", "ta", "co", "la", "thi", "ma", "va",
            "roi", "xong", "ve", "ke", "lai", "nghe", "noi", "hinh", "nhu", "kieu", "mot", "bo", "phim",
            "muon", "tim", "xem", "nho", "ten", "do", "ay", "nay", "kia", "trong", "cua", "cho", "voi",
            "duoc", "bi", "nen", "ra", "vao", "o", "sau", "truoc", "luc", "khi", "nao", "gi", "doan"
    );
    private static final Set<String> LOW_VALUE_SIGNAL_WORDS = Set.of(
            "ong", "ba", "cha", "me", "con", "an", "da", "vi", "di", "ve", "nha", "doi",
            "cap", "nam", "nu", "trai", "gai", "thanh", "pho", "hoc", "que", "lon", "nho",
            "rieng", "ngoai", "ben", "cung", "xem", "nhau", "hon", "chi", "vai", "ca", "den",
            "gia", "dinh", "tinh", "danh", "mao", "giau", "nhung", "khac", "nhan",
            "chau", "chuyen", "cuoc", "song", "cau", "nghia", "thay", "dua",
            "chup", "chung", "tam", "mai", "hanh", "dat", "moi"
    );
    private static final Set<String> GENERIC_PHRASE_TOKENS = Set.of(
            "anh", "chi", "em", "ong", "ba", "cha", "me", "con", "nguoi", "gia", "dinh",
            "danh", "tinh", "mao", "giau", "trai", "gai", "nam", "nu", "thanh", "pho", "nha", "que",
            "lon", "nho", "hoc", "di", "ve", "vao", "ra", "sau", "truoc", "nhung",
            "khac", "cung", "nhau", "do", "de", "bi", "co", "duoc", "nhan",
            "dau", "ten", "pham", "loan", "chau", "chuyen", "cuoc", "song", "cau", "nghia", "thay", "dua",
            "chup", "chung", "tam", "mai", "hanh", "dat", "moi"
    );
    private static final Set<String> PHRASE_FILLER_WORDS = Set.of(
            "toi", "minh", "em", "anh", "chi", "ban", "be", "nguoi", "ta", "co", "la", "thi", "va",
            "xong", "ve", "ke", "lai", "nghe", "noi", "hinh", "nhu", "kieu", "mot", "phim",
            "muon", "tim", "xem", "nho", "ten", "ay", "nay", "kia", "trong", "cua", "cho", "voi",
            "duoc", "nen", "ra", "vao", "o", "sau", "truoc", "luc", "khi", "nao", "gi", "doan"
    );

    private final MovieRepository movieRepository;
    private final CinemaRetrievalService retrievalService;
    private final MovieDiscoveryRerankService rerankService;
    private final boolean embeddingEnabled;
    private final String embeddingModelName;
    private final int rerankCandidateLimit;

    public MovieDiscoveryService(
            MovieRepository movieRepository,
            CinemaRetrievalService retrievalService,
            MovieDiscoveryRerankService rerankService,
            @Value("${cinema.movie-discovery.embedding-enabled:true}") boolean embeddingEnabled,
            @Value("${cinema.bot.embedding-model:nomic-embed-text}") String embeddingModelName,
            @Value("${cinema.movie-discovery.rerank-candidate-limit:12}") int rerankCandidateLimit
    ) {
        this.movieRepository = movieRepository;
        this.retrievalService = retrievalService;
        this.rerankService = rerankService;
        this.embeddingEnabled = embeddingEnabled;
        this.embeddingModelName = embeddingModelName;
        int configuredRerankLimit = rerankCandidateLimit > 0 ? rerankCandidateLimit : DEFAULT_RERANK_CANDIDATE_LIMIT;
        this.rerankCandidateLimit = Math.min(MAX_RERANK_CANDIDATE_LIMIT, Math.max(MIN_RERANK_CANDIDATE_LIMIT, configuredRerankLimit));
    }

    @Transactional
    public List<MovieDiscoveryResultDTO> discover(String query, Integer limit, boolean includeEnded) {
        long requestStartedAt = System.nanoTime();
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return Collections.emptyList();
        }

        int resolvedLimit = clampLimit(limit);
        List<Movie> movies = movieRepository.findAll().stream()
                .filter(movie -> includeEnded || movie.getStatus() != Movie.MovieStatus.ENDED)
                .collect(Collectors.toList());
        if (movies.isEmpty()) {
            return Collections.emptyList();
        }

        long semanticSearchTimeMs = 0L;
        Map<Long, Double> denseScores;
        if (embeddingEnabled) {
            long semanticSearchStartedAt = System.nanoTime();
            denseScores = findDenseScores(query, movies);
            semanticSearchTimeMs = elapsedMs(semanticSearchStartedAt);
        } else {
            denseScores = Collections.emptyMap();
        }
        List<String> queryTokens = tokenize(normalizedQuery);
        List<String> orderedQueryTokens = tokenizeInOrder(normalizedQuery);
        List<String> queryPhrases = buildQueryPhrases(normalizedQuery);
        Map<String, Integer> documentFrequency = buildDocumentFrequency(movies);
        List<MovieCandidate> scoredCandidates = new ArrayList<>();

        for (Movie movie : movies) {
            MovieCandidate candidate = scoreMovie(
                    movie,
                    normalizedQuery,
                    queryTokens,
                    orderedQueryTokens,
                    queryPhrases,
                    denseScores,
                    documentFrequency,
                    movies.size()
            );
            scoredCandidates.add(candidate);
        }

        scoredCandidates.sort(Comparator.comparingDouble(MovieCandidate::score).reversed());
        List<MovieCandidate> candidates = selectCandidatesForRerank(scoredCandidates, resolvedLimit);
        candidates.sort(Comparator.comparingDouble(MovieDiscoveryService::rerankPriorityScore).reversed());
        long rerankStartedAt = System.nanoTime();
        List<MovieCandidate> rankedCandidates = rerankCandidates(query, candidates, resolvedLimit);
        long rerankTimeMs = elapsedMs(rerankStartedAt);
        long processingTimeMs = elapsedMs(requestStartedAt);
        DiscoveryTiming timing = new DiscoveryTiming(
                processingTimeMs,
                semanticSearchTimeMs,
                rerankTimeMs
        );
        if (processingTimeMs > 5_000L) {
            log.warn("[MovieDiscovery] Slow discover request: totalMs={}, semanticMs={}, rerankMs={}, movies={}, candidates={}, limit={}",
                    processingTimeMs, semanticSearchTimeMs, rerankTimeMs, movies.size(), candidates.size(), resolvedLimit);
        }
        return rankedCandidates.stream()
                .limit(resolvedLimit)
                .map(candidate -> toResponse(candidate, timing))
                .collect(Collectors.toList());
    }

    private MovieCandidate scoreMovie(Movie movie,
                                      String normalizedQuery,
                                      List<String> queryTokens,
                                      List<String> orderedQueryTokens,
                                      List<String> queryPhrases,
                                      Map<Long, Double> denseScores,
                                      Map<String, Integer> documentFrequency,
                                      int documentCount) {
        String title = normalize(movie.getTitle());
        String genre = normalize(movie.getGenre());
        String description = normalize(movie.getDescription());
        String actors = normalize(movie.getActors());
        String document = String.join(" ", title, genre, description, actors).trim();
        Set<String> titleTokens = new LinkedHashSet<>(tokenize(title));
        Set<String> genreTokens = new LinkedHashSet<>(tokenize(genre));
        Set<String> descriptionTokens = new LinkedHashSet<>(tokenize(description));
        Set<String> actorTokens = new LinkedHashSet<>(tokenize(actors));
        List<String> orderedDocumentTokens = tokenizeInOrder(document);
        Set<String> documentTokens = new LinkedHashSet<>();
        documentTokens.addAll(titleTokens);
        documentTokens.addAll(genreTokens);
        documentTokens.addAll(descriptionTokens);
        documentTokens.addAll(actorTokens);

        double score = 0.0;
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        LinkedHashSet<String> signals = new LinkedHashSet<>();

        boolean exactTitleQuery = isExactTitleQuery(normalizedQuery, title);
        if (exactTitleQuery) {
            score += EXACT_TITLE_QUERY_SCORE;
            reasons.add("Khớp trực tiếp với tên phim");
            signals.add(title);
        }

        double titlePhraseScore = 0.0;
        double genrePhraseScore = 0.0;
        double descriptionPhraseScore = 0.0;
        double actorPhraseScore = 0.0;
        for (String phrase : queryPhrases) {
            if (phrase.length() < 4) continue;
            double phraseWeight = phraseMatchWeight(phrase);
            if (phraseWeight <= 0.0) continue;

            if (title.contains(phrase)) {
                titlePhraseScore += 30.0 * phraseWeight;
                reasons.add("Khớp với tên phim hoặc tên nhân vật bạn mô tả");
                signals.add(phrase);
            }
            if (genre.contains(phrase)) {
                genrePhraseScore += 10.0 * phraseWeight;
                reasons.add("Khớp thể loại phim");
                signals.add(phrase);
            }
            if (description.contains(phrase)) {
                descriptionPhraseScore += 18.0 * phraseWeight;
                reasons.add("Khớp tình huống trong mô tả phim");
                signals.add(phrase);
            }
            if (actors.contains(phrase)) {
                actorPhraseScore += 10.0 * phraseWeight;
                reasons.add("Khớp diễn viên liên quan");
                signals.add(phrase);
            }
        }

        score += Math.min(30.0, titlePhraseScore);
        score += Math.min(10.0, genrePhraseScore);
        score += Math.min(28.0, descriptionPhraseScore);
        score += Math.min(10.0, actorPhraseScore);

        int titleHits = 0;
        int genreHits = 0;
        int descriptionHits = 0;
        int actorHits = 0;
        double titleTokenScore = 0.0;
        double genreTokenScore = 0.0;
        double descriptionTokenScore = 0.0;
        double actorTokenScore = 0.0;
        for (String token : queryTokens) {
            boolean matched = false;
            double tokenWeight = tokenMatchWeight(token) * inverseDocumentFrequencyWeight(token, documentFrequency, documentCount);
            if (titleTokens.contains(token) && isUsefulTokenMatch(token)) {
                titleTokenScore += 7.0 * tokenWeight;
                titleHits++;
                matched = true;
            }
            if (genreTokens.contains(token)) {
                genreTokenScore += 3.5 * tokenWeight;
                genreHits++;
                matched = true;
            }
            if (descriptionTokens.contains(token)) {
                descriptionTokenScore += 2.4 * tokenWeight;
                descriptionHits++;
                matched = true;
            }
            if (actorTokens.contains(token)) {
                actorTokenScore += 4.0 * tokenWeight;
                actorHits++;
                matched = true;
            }
            if (matched && signals.size() < 6) {
                signals.add(token);
            }
        }
        score += Math.min(16.0, titleTokenScore);
        score += Math.min(8.0, genreTokenScore);
        score += Math.min(22.0, descriptionTokenScore);
        score += Math.min(10.0, actorTokenScore);

        if (titleHits > 0) reasons.add("Có chi tiết khớp với tên phim");
        if (genreHits > 0) reasons.add("Khớp thể loại phim");
        if (descriptionHits > 0) reasons.add("Có nhiều chi tiết gần với nội dung phim");
        if (actorHits > 0) reasons.add("Có diễn viên liên quan");

        score += Math.min(45.0, scoreGenreIntents(normalizedQuery, genre, description, reasons, signals));

        double coverage = weightedTokenCoverage(queryTokens, documentTokens, documentFrequency, documentCount);
        score += coverage * 15.0;

        double phraseStrength = matchedPhraseStrength(queryPhrases, title, genre, description, actors);
        score += phraseStrength * 18.0;
        if (phraseStrength >= 0.35) {
            reasons.add("Có nhiều cụm chi tiết khớp nội dung phim");
        }

        double orderedCoverage = orderedTokenCoverage(orderedQueryTokens, orderedDocumentTokens, documentFrequency, documentCount);
        score += orderedCoverage * 16.0;
        if (orderedCoverage >= 0.28) {
            reasons.add("Các chi tiết xuất hiện gần đúng mạch bạn mô tả");
        }

        double proximityStrength = pairProximityStrength(orderedQueryTokens, orderedDocumentTokens, documentFrequency, documentCount);
        score += proximityStrength * 14.0;
        if (proximityStrength >= 0.35) {
            reasons.add("Các chi tiết đặc trưng xuất hiện gần nhau trong nội dung phim");
        }

        double repeatedStrength = repeatedDistinctiveTokenStrength(orderedQueryTokens, orderedDocumentTokens, documentFrequency, documentCount);
        score += repeatedStrength * 10.0;
        if (repeatedStrength >= 0.25) {
            reasons.add("Các chi tiết đặc trưng lặp lại trong nội dung phim");
        }

        double namedTokenStrength = namedQueryTokenStrength(normalizedQuery, orderedDocumentTokens, documentFrequency, documentCount);
        score += namedTokenStrength * 30.0;
        if (namedTokenStrength >= 0.35) {
            reasons.add("Khớp tên nhân vật hoặc tên riêng trong mô tả");
        }

        double denseScore = denseScores.getOrDefault(movie.getMovieId(), 0.0);
        if (denseScore > 0.0) {
            score += denseScore * 28.0;
            if (denseScore >= 0.70) {
                reasons.add("Gợi ý ngữ nghĩa từ nội dung phim");
                signals.add("semantic-model");
            }
        }

        if (movie.getStatus() == Movie.MovieStatus.NOW_SHOWING) {
            score += 0.5;
        } else if (movie.getStatus() == Movie.MovieStatus.SPECIAL_RELEASE) {
            score += 0.3;
        }

        if (reasons.isEmpty() && score > 0) {
            reasons.add("Có chi tiết gần với mô tả của bạn");
        }

        double denseEvidenceWeight = denseEvidenceWeight(queryTokens.size(), exactTitleQuery);
        return new MovieCandidate(movie, calibrateScore(score), denseScore, denseEvidenceWeight, null, null, null, null, List.copyOf(reasons), List.copyOf(signals));
    }

    private List<MovieCandidate> selectCandidatesForRerank(List<MovieCandidate> scoredCandidates, int requestedLimit) {
        if (scoredCandidates.isEmpty()) {
            return Collections.emptyList();
        }

        int recallWindow = Math.min(scoredCandidates.size(), Math.max(rerankCandidateLimit, requestedLimit));
        List<MovieCandidate> candidates = new ArrayList<>();
        Set<Long> selectedMovieIds = new LinkedHashSet<>();

        for (MovieCandidate candidate : scoredCandidates) {
            Long movieId = candidate.movie().getMovieId();
            if ((candidate.score() >= MIN_SCORE || candidate.denseScore() >= 0.62) && selectedMovieIds.add(movieId)) {
                candidates.add(candidate);
            }
        }

        for (MovieCandidate candidate : scoredCandidates) {
            if (candidates.size() >= recallWindow) {
                break;
            }
            Long movieId = candidate.movie().getMovieId();
            if (candidate.score() > 0.0 && selectedMovieIds.add(movieId)) {
                candidates.add(candidate);
            }
        }

        return candidates;
    }

    private static double rerankPriorityScore(MovieCandidate candidate) {
        double denseBoost = candidate.denseScore() > 0.0
                ? candidate.denseScore() * 20.0 + 8.0
                : 0.0;
        return candidate.score() + denseBoost;
    }

    private List<MovieCandidate> rerankCandidates(String query, List<MovieCandidate> candidates, int requestedLimit) {
        if (candidates.isEmpty()) {
            return candidates;
        }

        int windowSize = Math.min(candidates.size(), Math.max(rerankCandidateLimit, requestedLimit));
        List<MovieCandidate> rerankWindow = candidates.subList(0, windowSize);
        List<MovieDiscoveryRerankService.RerankCandidate> rerankPayload = rerankWindow.stream()
                .map(candidate -> MovieDiscoveryRerankService.RerankCandidate.from(
                        candidate.movie(),
                        candidate.score(),
                        candidate.denseScore()
                ))
                .collect(Collectors.toList());

        MovieDiscoveryRerankService.RerankResponse rerankResponse = rerankService.rerank(query, rerankPayload);
        if (!rerankResponse.hasScores()) {
            return candidates;
        }

        Map<Long, Double> normalizedRerankScores = normalizeRerankScores(rerankWindow, rerankResponse);
        List<MovieCandidate> rerankedWindow = rerankWindow.stream()
                .map(candidate -> applyRerankScore(candidate, rerankResponse, normalizedRerankScores))
                .sorted(Comparator.comparingDouble(MovieCandidate::score).reversed())
                .collect(Collectors.toCollection(ArrayList::new));

        if (windowSize < candidates.size()) {
            rerankedWindow.addAll(candidates.subList(windowSize, candidates.size()));
        }
        return rerankedWindow;
    }

    private MovieCandidate applyRerankScore(MovieCandidate candidate,
                                            MovieDiscoveryRerankService.RerankResponse rerankResponse,
                                            Map<Long, Double> normalizedRerankScores) {
        MovieDiscoveryRerankService.RerankScore rerankScore = rerankResponse.scores().get(candidate.movie().getMovieId());
        if (rerankScore == null) {
            return candidate;
        }

        double scoringRerankScore = normalizedRerankScores.getOrDefault(
                candidate.movie().getMovieId(),
                rerankScore.score()
        );
        double combinedScore = combineRerankScore(candidate, scoringRerankScore);
        List<String> reasons = new ArrayList<>(candidate.reasons());
        if (rerankScore.reason() != null && !rerankScore.reason().isBlank()) {
            reasons.add(0, rerankScore.reason());
        }

        return new MovieCandidate(
                candidate.movie(),
                combinedScore,
                candidate.denseScore(),
                candidate.denseEvidenceWeight(),
                rerankScore.score(),
                rerankScore.score(),
                rerankResponse.modelName(),
                rerankScore.reason(),
                List.copyOf(reasons),
                candidate.signals()
        );
    }

    private Map<Long, Double> normalizeRerankScores(List<MovieCandidate> candidates,
                                                    MovieDiscoveryRerankService.RerankResponse rerankResponse) {
        List<MovieCandidate> scoredCandidates = candidates.stream()
                .filter(candidate -> candidate.movie() != null)
                .filter(candidate -> candidate.movie().getMovieId() != null)
                .filter(candidate -> rerankResponse.scores().containsKey(candidate.movie().getMovieId()))
                .sorted((left, right) -> Double.compare(
                        rerankResponse.scores().get(right.movie().getMovieId()).score(),
                        rerankResponse.scores().get(left.movie().getMovieId()).score()
                ))
                .collect(Collectors.toList());

        Map<Long, Double> normalizedScores = new HashMap<>();
        if (scoredCandidates.isEmpty()) {
            return normalizedScores;
        }

        double minScore = scoredCandidates.stream()
                .mapToDouble(candidate -> rerankResponse.scores().get(candidate.movie().getMovieId()).score())
                .min()
                .orElse(0.0);
        double maxScore = scoredCandidates.stream()
                .mapToDouble(candidate -> rerankResponse.scores().get(candidate.movie().getMovieId()).score())
                .max()
                .orElse(0.0);
        double range = maxScore - minScore;

        if (maxScore <= LOW_CONFIDENCE_RERANK_TIE_BREAK_MAX_SCORE && range > 0.0) {
            return rankOnlyTieBreakScores(scoredCandidates);
        }

        if (range <= RERANK_RELATIVE_RANGE_EPSILON) {
            for (MovieCandidate candidate : scoredCandidates) {
                double rawScore = rerankResponse.scores().get(candidate.movie().getMovieId()).score();
                normalizedScores.put(candidate.movie().getMovieId(), clamp01(rawScore));
            }
            return normalizedScores;
        }

        double confidence = rerankConfidence(maxScore, range);
        for (int index = 0; index < scoredCandidates.size(); index++) {
            MovieCandidate candidate = scoredCandidates.get(index);
            double rawScore = rerankResponse.scores().get(candidate.movie().getMovieId()).score();
            double rankScore = scoredCandidates.size() == 1
                    ? 1.0
                    : 1.0 - (index / (double) (scoredCandidates.size() - 1));
            double rangeScore = range > RERANK_RELATIVE_RANGE_EPSILON
                    ? (rawScore - minScore) / range
                    : rankScore;
            double relativeScore = range > RERANK_RELATIVE_RANGE_EPSILON
                    ? rangeScore * 0.65 + rankScore * 0.35
                    : rankScore;
            double effectiveScore = rawScore + (relativeScore - rawScore) * confidence;
            normalizedScores.put(candidate.movie().getMovieId(), clamp01(effectiveScore));
        }

        return normalizedScores;
    }

    private Map<Long, Double> rankOnlyTieBreakScores(List<MovieCandidate> scoredCandidates) {
        Map<Long, Double> normalizedScores = new HashMap<>();
        for (int index = 0; index < scoredCandidates.size(); index++) {
            MovieCandidate candidate = scoredCandidates.get(index);
            double rankScore = scoredCandidates.size() == 1
                    ? 1.0
                    : 1.0 - (index / (double) (scoredCandidates.size() - 1));
            normalizedScores.put(candidate.movie().getMovieId(), 0.5 + rankScore * 0.5);
        }
        return normalizedScores;
    }

    private double combineRerankScore(MovieCandidate candidate, double rerankScore) {
        double rerankPercent = clamp01(rerankScore) * 100.0;
        double densePercent = candidate.denseScore() > 0.0 ? candidate.denseScore() * 100.0 : 0.0;
        double baselinePercent = candidate.score();

        double evidenceScore = densePercent > 0.0
                ? densePercent * candidate.denseEvidenceWeight() + baselinePercent * (1.0 - candidate.denseEvidenceWeight())
                : baselinePercent;
        double rerankTrust = confidenceBetween(
                clamp01(rerankScore),
                LOW_CONFIDENCE_RERANK_MAX_SCORE,
                TRUSTED_RERANK_SCORE_FULL
        );
        if (rerankTrust <= 0.0) {
            return Math.max(0.0, Math.min(100.0, evidenceScore));
        }

        double rerankWeight = densePercent > 0.0
                ? RERANK_TIE_BREAK_WEIGHT_WITH_DENSE
                : RERANK_TIE_BREAK_WEIGHT_WITHOUT_DENSE;
        double rerankAdjustment = (rerankPercent - RERANK_NEUTRAL_PERCENT) * rerankWeight * rerankTrust;
        double combined = evidenceScore + rerankAdjustment;
        return Math.max(0.0, Math.min(100.0, combined));
    }

    private double denseEvidenceWeight(int queryTokenCount, boolean exactTitleQuery) {
        if (exactTitleQuery) {
            return SHORT_QUERY_DENSE_EVIDENCE_WEIGHT;
        }
        double longQueryConfidence = confidenceBetween(
                queryTokenCount,
                DENSE_EVIDENCE_LONG_QUERY_START_TOKENS,
                DENSE_EVIDENCE_LONG_QUERY_FULL_TOKENS
        );
        return SHORT_QUERY_DENSE_EVIDENCE_WEIGHT
                + (LONG_QUERY_DENSE_EVIDENCE_WEIGHT - SHORT_QUERY_DENSE_EVIDENCE_WEIGHT) * longQueryConfidence;
    }

    private double rerankConfidence(double maxScore, double range) {
        if (maxScore < LOW_CONFIDENCE_RERANK_MAX_SCORE) {
            return 0.0;
        }

        double relativeConfidence = Math.max(
                confidenceBetween(maxScore, RERANK_SCORE_CONFIDENCE_START, RERANK_SCORE_CONFIDENCE_FULL),
                confidenceBetween(range, RERANK_RANGE_CONFIDENCE_START, RERANK_RANGE_CONFIDENCE_FULL)
        );
        double absoluteConfidence = confidenceBetween(
                maxScore,
                LOW_CONFIDENCE_RERANK_MAX_SCORE,
                TRUSTED_RERANK_SCORE_FULL
        );
        return relativeConfidence * absoluteConfidence;
    }

    private double confidenceBetween(double value, double start, double full) {
        if (value <= start) {
            return 0.0;
        }
        if (value >= full) {
            return 1.0;
        }
        double progress = (value - start) / (full - start);
        return progress * progress * (3.0 - 2.0 * progress);
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double calibrateScore(double rawScore) {
        if (rawScore <= 0.0) {
            return 0.0;
        }
        double calibrated = 100.0 * (1.0 - Math.exp(-rawScore / 70.0));
        return Math.min(99.9, calibrated);
    }

    private double scoreGenreIntents(String query,
                                     String genre,
                                     String description,
                                     LinkedHashSet<String> reasons,
                                     LinkedHashSet<String> signals) {
        double score = 0.0;
        String movieText = String.join(" ", genre, description).trim();
        Map<String, IntentProfile> profiles = intentProfiles();
        for (Map.Entry<String, IntentProfile> entry : profiles.entrySet()) {
            IntentProfile profile = entry.getValue();
            Set<String> queryMatches = matchedIntentTerms(profile.queryTerms(), query);
            if (queryMatches.size() < profile.minQueryHits()) continue;

            Set<String> movieMatches = matchedIntentTerms(profile.movieTerms(), movieText);
            int evidenceGroups = matchedEvidenceGroupCount(profile, query, movieText);
            if (movieMatches.size() < profile.minMovieHits() || evidenceGroups < profile.minEvidenceGroups()) continue;

            double queryStrength = evidenceStrength(queryMatches.size(), profile.minQueryHits(), profile.queryTerms().size());
            double movieStrength = evidenceStrength(movieMatches.size(), profile.minMovieHits(), profile.movieTerms().size());
            double groupStrength = profile.evidenceGroups().isEmpty()
                    ? 1.0
                    : evidenceGroups / (double) profile.evidenceGroups().size();
            double intentStrength = profile.evidenceGroups().isEmpty()
                    ? Math.sqrt(queryStrength * movieStrength)
                    : queryStrength * 0.20 + movieStrength * 0.35 + groupStrength * 0.45;

            score += profile.weight() * Math.max(0.45, Math.min(1.0, intentStrength));
            reasons.add(profile.reason());
            signals.add(entry.getKey());
        }
        return score;
    }

    private Set<String> matchedIntentTerms(List<String> terms, String text) {
        if (terms == null || terms.isEmpty() || text == null || text.isBlank()) {
            return Collections.emptySet();
        }

        return terms.stream()
                .filter(this::isUsefulIntentTerm)
                .filter(term -> containsIntentTerm(text, term))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private int matchedEvidenceGroupCount(IntentProfile profile, String query, String movieText) {
        if (profile.evidenceGroups().isEmpty()) {
            return 0;
        }

        int count = 0;
        for (IntentEvidenceGroup group : profile.evidenceGroups()) {
            boolean queryMatchesGroup = group.queryTerms().stream()
                    .filter(this::isUsefulIntentTerm)
                    .anyMatch(term -> containsIntentTerm(query, term));
            if (!queryMatchesGroup) {
                continue;
            }

            boolean movieMatchesGroup = group.movieTerms().stream()
                    .filter(this::isUsefulIntentTerm)
                    .anyMatch(term -> containsIntentTerm(movieText, term));
            if (movieMatchesGroup) {
                count++;
            }
        }
        return count;
    }

    private double evidenceStrength(int hits, int minimumHits, int availableTerms) {
        if (hits <= 0) {
            return 0.0;
        }
        int usefulTarget = Math.max(minimumHits, Math.min(availableTerms, minimumHits + 3));
        return Math.min(1.0, hits / (double) usefulTarget);
    }

    private boolean containsIntentTerm(String text, String term) {
        if (text == null || term == null || text.isBlank() || term.isBlank()) {
            return false;
        }
        return containsWholePhrase(text, normalize(term));
    }

    private Map<String, IntentProfile> intentProfiles() {
        Map<String, IntentProfile> profiles = new HashMap<>();
        profiles.put("siêu anh hùng", new IntentProfile(
                List.of("siêu anh hùng", "anh hùng", "avenger", "người nhện", "batman", "wolverine"),
                List.of("siêu anh hùng", "hành động", "avenger", "batman", "spider", "wolverine"),
                14.0,
                "Khớp nhóm phim siêu anh hùng"
        ));
        profiles.put("gia đình", new IntentProfile(
                List.of("gia đình", "ba", "mẹ", "cha", "con", "ngoại", "nội", "chăm sóc", "cảm động"),
                List.of("gia đình", "chăm sóc", "ba", "mẹ", "cha", "con", "cảm động"),
                12.0,
                "Khớp câu chuyện gia đình"
        ));
        profiles.put("kinh dị", new IntentProfile(
                List.of("kinh dị", "sợ", "ma", "quái vật", "sinh vật", "im lặng", "âm thanh"),
                List.of("kinh dị", "giật gân", "sinh vật", "quái vật", "âm thanh"),
                12.0,
                "Khớp không khí kinh dị, căng thẳng"
        ));
        profiles.put("tình cảm", new IntentProfile(
                List.of("tình yêu", "yêu", "lãng mạn", "hẹn hò", "đôi lứa", "cảm tình"),
                List.of("tình cảm", "lãng mạn", "tâm lý", "tình yêu"),
                10.0,
                "Khớp sắc thái tình cảm"
        ));
        profiles.put("thanh mai trúc mã", new IntentProfile(
                List.of("thanh mai trúc mã", "bạn từ nhỏ", "lớn lên từ nhỏ", "cặp đôi", "ở quê", "làng quê",
                        "lên thành phố", "đi học", "dụ dỗ", "tán tỉnh", "con riêng", "mang thai",
                        "sinh con", "con gái", "bỏ rơi", "nhận nuôi", "chăm sóc", "chăm lo", "đến lớn"),
                List.of("thanh mai trúc mã", "bạn từ nhỏ", "lớn lên", "thời học trò", "làng quê", "quê nghèo",
                        "lên thành phố", "thành phố Huế", "đi học", "tán tỉnh", "ngã vào vòng tay", "mang thai",
                        "nghỉ học", "sinh con", "con gái", "cưới một cô gái khác", "bỏ rơi", "chăm sóc",
                        "chăm lo", "nuôi", "lớn lên"),
                34.0,
                "Khớp mạch chuyện tình từ thời thơ ấu",
                4,
                6,
                3,
                List.of(
                        new IntentEvidenceGroup(
                                List.of("thanh mai trúc mã", "bạn từ nhỏ", "lớn lên từ nhỏ", "ở quê", "làng quê"),
                                List.of("thanh mai trúc mã", "bạn từ nhỏ", "lớn lên", "thời học trò", "làng quê", "quê nghèo")
                        ),
                        new IntentEvidenceGroup(
                                List.of("lên thành phố", "đi học", "ở quê"),
                                List.of("lên thành phố", "thành phố Huế", "đi học", "ở quê")
                        ),
                        new IntentEvidenceGroup(
                                List.of("dụ dỗ", "tán tỉnh", "con riêng", "mang thai", "sinh con", "con gái"),
                                List.of("tán tỉnh", "ngã vào vòng tay", "mang thai", "nghỉ học", "sinh con", "con gái")
                        ),
                        new IntentEvidenceGroup(
                                List.of("bỏ rơi", "nhận nuôi", "chăm sóc", "chăm lo", "đến lớn"),
                                List.of("bỏ rơi", "cưới một cô gái khác", "chăm sóc", "chăm lo", "nuôi", "lớn lên")
                        )
                )
        ));
        profiles.put("hài", new IntentProfile(
                List.of("hài", "vui", "cười", "vui nhộn", "giải trí"),
                List.of("hài", "hoạt hình", "vui", "gia đình"),
                9.0,
                "Khớp nhu cầu xem phim nhẹ nhàng, giải trí"
        ));
        profiles.put("khoa học viễn tưởng", new IntentProfile(
                List.of("vũ trụ", "hành tinh", "ngoài hành tinh", "đa vũ trụ", "thời gian", "công nghệ"),
                List.of("khoa học viễn tưởng", "vũ trụ", "hành tinh", "đa vũ trụ", "thời gian"),
                12.0,
                "Khớp bối cảnh khoa học viễn tưởng"
        ));
        profiles.put("giấc mơ ý tưởng", new IntentProfile(
                List.of("giấc mơ", "xâm nhập giấc mơ", "xâm nhập", "cấy ý tưởng", "cấy một ý tưởng", "tiềm thức"),
                List.of("giấc mơ", "xâm nhập", "tiềm thức", "đánh cắp thông tin", "cấy một ý tưởng", "ý tưởng"),
                14.0,
                "Khớp ý đồ xâm nhập giấc mơ hoặc tiềm thức"
        ));
        profiles.put("tội phạm", new IntentProfile(
                List.of("tội phạm", "giết người", "trả thù", "tham nhũng", "kẻ thù", "bí mật"),
                List.of("tội phạm", "tham nhũng", "giết người", "trả thù", "bí mật"),
                10.0,
                "Khớp màu sắc tội phạm, điều tra"
        ));
        profiles.put("giả mạo xâm nhập", new IntentProfile(
                List.of("gia đình nghèo", "nghèo", "giả mạo", "giả mạo danh tính", "đóng giả", "giả danh",
                        "xâm nhập", "vào nhà", "nhà giàu", "gia đình giàu", "thượng lưu"),
                List.of("nghèo", "thất nghiệp", "bán hầm", "kiếm sống", "giả vờ", "đóng giả", "giả mạo",
                        "lập mưu", "thủ đoạn", "thế chỗ", "nhận làm gia sư", "gia sư", "quản gia",
                        "thượng lưu", "giàu có", "biệt thự", "nhà giàu"),
                40.0,
                "Khớp mạch giả mạo danh tính để xâm nhập",
                3,
                5,
                3,
                List.of(
                        new IntentEvidenceGroup(
                                List.of("gia đình nghèo", "nghèo"),
                                List.of("nghèo", "thất nghiệp", "bán hầm", "kiếm sống")
                        ),
                        new IntentEvidenceGroup(
                                List.of("nhà giàu", "gia đình giàu", "thượng lưu"),
                                List.of("thượng lưu", "giàu có", "biệt thự", "nhà giàu")
                        ),
                        new IntentEvidenceGroup(
                                List.of("giả mạo", "giả mạo danh tính", "đóng giả", "giả danh"),
                                List.of("giả vờ", "đóng giả", "giả mạo", "lập mưu", "thủ đoạn")
                        ),
                        new IntentEvidenceGroup(
                                List.of("xâm nhập", "vào nhà"),
                                List.of("thế chỗ", "nhận làm gia sư", "gia sư", "quản gia", "đuổi việc", "biệt thự")
                        )
                )
        ));
        return profiles;
    }

    private Map<Long, Double> findDenseScores(String query, List<Movie> movies) {
        try {
            return retrievalService.denseSearchMoviesUsingExistingEmbeddings(query, movies).stream()
                    .filter(candidate -> candidate.item() != null && candidate.item().getMovieId() != null)
                    .collect(Collectors.toMap(
                            candidate -> candidate.item().getMovieId(),
                            CinemaRetrievalService.DenseCandidate::score,
                            Math::max
                    ));
        } catch (RuntimeException ex) {
            return Collections.emptyMap();
        }
    }

    private List<String> buildQueryPhrases(String normalizedQuery) {
        LinkedHashSet<String> phrases = new LinkedHashSet<>();
        if (normalizedQuery.length() >= 4 && normalizedQuery.length() <= 80) {
            phrases.add(normalizedQuery);
        }
        List<String> queryWords = splitWords(normalizedQuery);
        int maxPhraseSize = Math.min(MAX_QUERY_PHRASE_SIZE, queryWords.size());
        for (int size = maxPhraseSize; size >= 2; size--) {
            for (int i = 0; i <= queryWords.size() - size; i++) {
                String phrase = String.join(" ", queryWords.subList(i, i + size));
                if (phraseMatchWeight(phrase) > 0.0) {
                    phrases.add(phrase);
                }
            }
        }
        return List.copyOf(phrases);
    }

    private Map<String, Integer> buildDocumentFrequency(List<Movie> movies) {
        Map<String, Integer> documentFrequency = new HashMap<>();
        if (movies == null || movies.isEmpty()) {
            return documentFrequency;
        }

        for (Movie movie : movies) {
            if (movie == null) continue;
            Set<String> tokens = new LinkedHashSet<>();
            tokens.addAll(tokenize(normalize(movie.getTitle())));
            tokens.addAll(tokenize(normalize(movie.getGenre())));
            tokens.addAll(tokenize(normalize(movie.getDescription())));
            tokens.addAll(tokenize(normalize(movie.getActors())));
            for (String token : tokens) {
                documentFrequency.merge(token, 1, Integer::sum);
            }
        }
        return documentFrequency;
    }

    private double weightedTokenCoverage(List<String> queryTokens,
                                         Set<String> documentTokens,
                                         Map<String, Integer> documentFrequency,
                                         int documentCount) {
        if (queryTokens == null || queryTokens.isEmpty() || documentTokens == null || documentTokens.isEmpty()) {
            return 0.0;
        }
        double matchedWeight = 0.0;
        double totalWeight = 0.0;
        for (String token : queryTokens) {
            double tokenWeight = tokenMatchWeight(token) * inverseDocumentFrequencyWeight(token, documentFrequency, documentCount);
            totalWeight += tokenWeight;
            if (documentTokens.contains(token)) {
                matchedWeight += tokenWeight;
            }
        }
        return totalWeight > 0.0 ? matchedWeight / totalWeight : 0.0;
    }

    private double matchedPhraseStrength(List<String> queryPhrases,
                                         String title,
                                         String genre,
                                         String description,
                                         String actors) {
        if (queryPhrases == null || queryPhrases.isEmpty()) {
            return 0.0;
        }

        double matchedWeight = 0.0;
        for (String phrase : queryPhrases) {
            double weight = phraseMatchWeight(phrase);
            if (weight <= 0.0) {
                continue;
            }
            if (description.contains(phrase)) {
                matchedWeight += weight;
            } else if (title.contains(phrase) || genre.contains(phrase) || actors.contains(phrase)) {
                matchedWeight += weight * 0.85;
            }
        }
        return Math.min(1.0, matchedWeight / 8.0);
    }

    private double orderedTokenCoverage(List<String> queryTokens,
                                        List<String> documentTokens,
                                        Map<String, Integer> documentFrequency,
                                        int documentCount) {
        if (queryTokens == null || queryTokens.isEmpty() || documentTokens == null || documentTokens.isEmpty()) {
            return 0.0;
        }

        int documentIndex = 0;
        double matchedWeight = 0.0;
        double totalWeight = 0.0;
        for (String token : queryTokens) {
            double tokenWeight = sequenceTokenWeight(token) * inverseDocumentFrequencyWeight(token, documentFrequency, documentCount);
            if (tokenWeight <= 0.0) {
                continue;
            }
            totalWeight += tokenWeight;
            int foundAt = indexOfToken(documentTokens, token, documentIndex);
            if (foundAt >= 0) {
                matchedWeight += tokenWeight;
                documentIndex = foundAt + 1;
            }
        }
        return totalWeight > 0.0 ? matchedWeight / totalWeight : 0.0;
    }

    private double pairProximityStrength(List<String> queryTokens,
                                         List<String> documentTokens,
                                         Map<String, Integer> documentFrequency,
                                         int documentCount) {
        if (queryTokens == null || queryTokens.isEmpty() || documentTokens == null || documentTokens.isEmpty()) {
            return 0.0;
        }

        List<String> distinctiveQueryTokens = queryTokens.stream()
                .filter(this::isDistinctivePhraseToken)
                .distinct()
                .limit(8)
                .collect(Collectors.toList());
        if (distinctiveQueryTokens.size() < 2) {
            return 0.0;
        }

        double matchedWeight = 0.0;
        double totalWeight = 0.0;
        for (int leftIndex = 0; leftIndex < distinctiveQueryTokens.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < distinctiveQueryTokens.size(); rightIndex++) {
                String left = distinctiveQueryTokens.get(leftIndex);
                String right = distinctiveQueryTokens.get(rightIndex);
                double pairWeight = (inverseDocumentFrequencyWeight(left, documentFrequency, documentCount)
                        + inverseDocumentFrequencyWeight(right, documentFrequency, documentCount)) / 2.0;
                totalWeight += pairWeight;
                int closeMatches = countCloseTokenPairs(documentTokens, left, right, 45);
                if (closeMatches > 0) {
                    matchedWeight += pairWeight * Math.min(1.0, closeMatches / 2.0);
                }
            }
        }
        return totalWeight > 0.0 ? Math.min(1.0, matchedWeight / totalWeight) : 0.0;
    }

    private double repeatedDistinctiveTokenStrength(List<String> queryTokens,
                                                    List<String> documentTokens,
                                                    Map<String, Integer> documentFrequency,
                                                    int documentCount) {
        if (queryTokens == null || queryTokens.isEmpty() || documentTokens == null || documentTokens.isEmpty()) {
            return 0.0;
        }

        List<String> distinctiveQueryTokens = queryTokens.stream()
                .filter(this::isDistinctivePhraseToken)
                .distinct()
                .limit(12)
                .collect(Collectors.toList());
        if (distinctiveQueryTokens.isEmpty()) {
            return 0.0;
        }

        double matchedWeight = 0.0;
        double totalWeight = 0.0;
        for (String token : distinctiveQueryTokens) {
            double tokenWeight = inverseDocumentFrequencyWeight(token, documentFrequency, documentCount);
            totalWeight += tokenWeight;
            int occurrences = countTokenOccurrences(documentTokens, token);
            if (occurrences > 0) {
                double occurrenceStrength = Math.min(1.0, 0.35 + Math.log1p(occurrences) / Math.log(8.0));
                matchedWeight += tokenWeight * occurrenceStrength;
            }
        }
        return totalWeight > 0.0 ? Math.min(1.0, matchedWeight / totalWeight) : 0.0;
    }

    private int countTokenOccurrences(List<String> tokens, String token) {
        int count = 0;
        for (String current : tokens) {
            if (current.equals(token)) {
                count++;
            }
        }
        return count;
    }

    private double namedQueryTokenStrength(String normalizedQuery,
                                           List<String> documentTokens,
                                           Map<String, Integer> documentFrequency,
                                           int documentCount) {
        if (normalizedQuery == null || normalizedQuery.isBlank() || documentTokens == null || documentTokens.isEmpty()) {
            return 0.0;
        }

        List<String> namedTokens = namedTokensFromQuery(normalizedQuery);
        if (namedTokens.isEmpty()) {
            return 0.0;
        }

        double matchedWeight = 0.0;
        double totalWeight = 0.0;
        for (String token : namedTokens) {
            double tokenWeight = inverseDocumentFrequencyWeight(token, documentFrequency, documentCount);
            totalWeight += tokenWeight;
            int occurrences = countTokenOccurrences(documentTokens, token);
            if (occurrences > 0) {
                double occurrenceStrength = Math.min(1.0, 0.55 + Math.log1p(occurrences) / Math.log(6.0));
                matchedWeight += tokenWeight * occurrenceStrength;
            }
        }

        return totalWeight > 0.0 ? Math.min(1.0, matchedWeight / totalWeight) : 0.0;
    }

    private List<String> namedTokensFromQuery(String normalizedQuery) {
        List<String> words = splitWords(normalizedQuery);
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (int index = 0; index < words.size() - 1; index++) {
            if (!"ten".equals(words.get(index))) {
                continue;
            }
            for (int nextIndex = index + 1; nextIndex < words.size(); nextIndex++) {
                String token = words.get(nextIndex);
                if (PHRASE_FILLER_WORDS.contains(token) || LOW_VALUE_SIGNAL_WORDS.contains(token)) {
                    continue;
                }
                if (isDistinctivePhraseToken(token)) {
                    tokens.add(token);
                }
                break;
            }
        }
        return List.copyOf(tokens);
    }

    private int countCloseTokenPairs(List<String> tokens, String left, String right, int maxDistance) {
        List<Integer> leftPositions = tokenPositions(tokens, left);
        List<Integer> rightPositions = tokenPositions(tokens, right);
        int matches = 0;
        for (Integer leftPosition : leftPositions) {
            for (Integer rightPosition : rightPositions) {
                int distance = Math.abs(leftPosition - rightPosition);
                if (distance > 0 && distance <= maxDistance) {
                    matches++;
                    break;
                }
            }
        }
        return matches;
    }

    private List<Integer> tokenPositions(List<String> tokens, String token) {
        List<Integer> positions = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index++) {
            if (tokens.get(index).equals(token)) {
                positions.add(index);
            }
        }
        return positions;
    }

    private int indexOfToken(List<String> tokens, String token, int fromIndex) {
        for (int index = Math.max(0, fromIndex); index < tokens.size(); index++) {
            if (tokens.get(index).equals(token)) {
                return index;
            }
        }
        return -1;
    }

    private double phraseMatchWeight(String phrase) {
        List<String> tokens = splitWords(phrase);
        if (tokens.isEmpty()) {
            return 0.0;
        }

        long strongTokens = tokens.stream()
                .filter(this::isStrongPhraseToken)
                .count();
        long distinctiveTokens = tokens.stream()
                .filter(this::isDistinctivePhraseToken)
                .count();
        long contextTokens = tokens.stream()
                .filter(this::isContextPhraseToken)
                .count();
        if (distinctiveTokens == 0 || (strongTokens == 0 && contextTokens < 2)) {
            return 0.0;
        }
        if (tokens.size() == 1 && tokens.get(0).length() < 5) {
            return 0.0;
        }

        double strongRatio = strongTokens / (double) tokens.size();
        double distinctiveRatio = distinctiveTokens / (double) tokens.size();
        double contextRatio = contextTokens / (double) tokens.size();
        double lengthBonus = Math.min(0.18, Math.max(0, tokens.size() - 2) * 0.04);
        return Math.min(1.0, 0.18 + distinctiveRatio * 0.45 + strongRatio * 0.25 + contextRatio * 0.12 + lengthBonus);
    }

    private boolean isExactTitleQuery(String normalizedQuery, String title) {
        if (normalizedQuery == null || title == null || title.isBlank()) {
            return false;
        }
        if (normalizedQuery.equals(title)) {
            return true;
        }
        if (title.length() < 5) {
            return false;
        }
        return containsWholePhrase(normalizedQuery, title)
                || (normalizedQuery.length() >= 5 && containsWholePhrase(title, normalizedQuery));
    }

    private boolean containsWholePhrase(String text, String phrase) {
        if (text == null || phrase == null || text.isBlank() || phrase.isBlank()) {
            return false;
        }
        return (" " + text + " ").contains(" " + phrase + " ");
    }

    private double inverseDocumentFrequencyWeight(String token, Map<String, Integer> documentFrequency, int documentCount) {
        if (token == null || token.isBlank() || documentCount <= 0 || documentFrequency == null || documentFrequency.isEmpty()) {
            return 1.0;
        }

        int frequency = documentFrequency.getOrDefault(token, 0);
        double weight = Math.log(1.0 + (documentCount + 1.0) / (frequency + 1.0));
        return Math.max(0.65, Math.min(2.5, weight));
    }

    private List<String> tokenize(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(NON_WORD_PATTERN.split(normalizedText))
                .map(String::trim)
                .filter(token -> token.length() > 1)
                .filter(token -> !STOP_WORDS.contains(token))
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> tokenizeInOrder(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return Collections.emptyList();
        }
        return splitWords(normalizedText).stream()
                .filter(token -> sequenceTokenWeight(token) > 0.0)
                .collect(Collectors.toList());
    }

    private List<String> splitWords(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(NON_WORD_PATTERN.split(normalizedText))
                .map(String::trim)
                .filter(token -> token.length() > 1)
                .collect(Collectors.toList());
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        String noAccent = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return NON_WORD_PATTERN.matcher(noAccent.replace('đ', 'd'))
                .replaceAll(" ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private MovieDiscoveryResultDTO toResponse(MovieCandidate candidate, DiscoveryTiming timing) {
        Movie movie = candidate.movie();
        List<String> signals = candidate.signals().stream()
                .filter(Objects::nonNull)
                .filter(signal -> !signal.isBlank())
                .filter(this::isUsefulSignal)
                .limit(6)
                .collect(Collectors.toList());
        String reason = candidate.reasons().isEmpty()
                ? "Có chi tiết gần với mô tả của bạn"
                : String.join(", ", candidate.reasons().stream().limit(3).toList());

        String matchSource = candidate.rerankScore() != null
                ? candidate.denseScore() > 0.0 ? "EMBEDDING_MODEL+RERANK" : "RERANK"
                : candidate.denseScore() > 0.0 ? "EMBEDDING_MODEL" : "LOCAL_SCORING";
        String modelName = candidate.rerankScore() != null
                ? joinModelNames(candidate.denseScore() > 0.0 ? embeddingModelName : null, candidate.rerankModelName())
                : candidate.denseScore() > 0.0 ? embeddingModelName : null;

        return MovieDiscoveryResultDTO.builder()
                .movieId(movie.getMovieId())
                .title(movie.getTitle())
                .duration(movie.getDuration())
                .genre(movie.getGenre())
                .description(movie.getDescription())
                .posterUrl(movie.getPosterUrl())
                .trailerUrl(movie.getTrailerUrl())
                .status(movie.getStatus() != null ? movie.getStatus().name() : null)
                .ageRating(movie.getAgeRating() != null ? movie.getAgeRating().name() : null)
                .actors(movie.getActors())
                .score(Math.round(candidate.score() * 10.0) / 10.0)
                .semanticScore(candidate.denseScore() > 0.0 ? Math.round(candidate.denseScore() * 1000.0) / 1000.0 : null)
                .rerankScore(candidate.rerankScore() != null ? Math.round(candidate.rerankScore() * 1000.0) / 1000.0 : null)
                .rawRerankScore(candidate.rawRerankScore() != null ? Math.round(candidate.rawRerankScore() * 1000.0) / 1000.0 : null)
                .processingTimeMs(timing.processingTimeMs())
                .semanticSearchTimeMs(timing.semanticSearchTimeMs())
                .rerankTimeMs(timing.rerankTimeMs())
                .matchReason(reason)
                .matchSource(matchSource)
                .modelName(modelName)
                .matchedSignals(signals)
                .build();
    }

    private String joinModelNames(String embeddingModel, String rerankModel) {
        if (rerankModel == null || rerankModel.isBlank()) {
            return embeddingModel;
        }
        if (embeddingModel == null || embeddingModel.isBlank()) {
            return rerankModel;
        }
        return embeddingModel + " + " + rerankModel;
    }

    private boolean isUsefulSignal(String signal) {
        if ("semantic-model".equals(signal)) {
            return true;
        }
        if (signal.contains(" ")) {
            return signal.length() >= 4;
        }
        return signal.length() >= 4 && !LOW_VALUE_SIGNAL_WORDS.contains(signal);
    }

    private boolean isUsefulTokenMatch(String token) {
        return token != null && token.length() >= 3 && !LOW_VALUE_SIGNAL_WORDS.contains(token);
    }

    private boolean isStrongPhraseToken(String token) {
        return token != null
                && token.length() >= 3
                && !STOP_WORDS.contains(token)
                && !LOW_VALUE_SIGNAL_WORDS.contains(token);
    }

    private boolean isDistinctivePhraseToken(String token) {
        return isStrongPhraseToken(token) && !GENERIC_PHRASE_TOKENS.contains(token);
    }

    private boolean isContextPhraseToken(String token) {
        return token != null
                && token.length() >= 2
                && !PHRASE_FILLER_WORDS.contains(token);
    }

    private double tokenMatchWeight(String token) {
        return isUsefulTokenMatch(token) ? 1.0 : 0.25;
    }

    private double sequenceTokenWeight(String token) {
        if (token == null || token.length() < 3 || PHRASE_FILLER_WORDS.contains(token)) {
            return 0.0;
        }
        return LOW_VALUE_SIGNAL_WORDS.contains(token) || STOP_WORDS.contains(token) ? 0.35 : 1.0;
    }

    private boolean isUsefulIntentTerm(String term) {
        return term != null && (term.contains(" ") || isUsefulTokenMatch(term));
    }

    private record IntentProfile(
            List<String> queryTerms,
            List<String> movieTerms,
            double weight,
            String reason,
            int minQueryHits,
            int minMovieHits,
            int minEvidenceGroups,
            List<IntentEvidenceGroup> evidenceGroups
    ) {
        private IntentProfile(List<String> queryTerms, List<String> movieTerms, double weight, String reason) {
            this(queryTerms, movieTerms, weight, reason, 1, 1, 0, Collections.emptyList());
        }
    }

    private record IntentEvidenceGroup(List<String> queryTerms, List<String> movieTerms) {
    }

    private long elapsedMs(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private record DiscoveryTiming(long processingTimeMs, long semanticSearchTimeMs, long rerankTimeMs) {
    }

    private record MovieCandidate(
            Movie movie,
            double score,
            double denseScore,
            double denseEvidenceWeight,
            Double rerankScore,
            Double rawRerankScore,
            String rerankModelName,
            String rerankReason,
            List<String> reasons,
            List<String> signals
    ) {
    }
}
