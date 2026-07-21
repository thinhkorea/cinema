package com.example.cinema.service;

import com.example.cinema.domain.Movie;
import com.example.cinema.dto.MovieDiscoveryResultDTO;
import com.example.cinema.repository.MovieRepository;
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

    private static final int DEFAULT_LIMIT = 1;
    private static final int MAX_LIMIT = 12;
    private static final int DEFAULT_RERANK_CANDIDATE_LIMIT = 5;
    private static final double MIN_SCORE = 5.0;
    private static final Pattern NON_WORD_PATTERN = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "toi", "minh", "em", "anh", "chi", "ban", "be", "nguoi", "ta", "co", "la", "thi", "ma", "va",
            "roi", "xong", "ve", "ke", "lai", "nghe", "noi", "hinh", "nhu", "kieu", "mot", "bo", "phim",
            "muon", "tim", "xem", "nho", "ten", "do", "ay", "nay", "kia", "trong", "cua", "cho", "voi",
            "duoc", "bi", "nen", "ra", "vao", "o", "sau", "truoc", "luc", "khi", "nao", "gi", "doan"
    );
    private static final Set<String> LOW_VALUE_SIGNAL_WORDS = Set.of(
            "ong", "ba", "cha", "me", "con", "an", "da", "vi", "di", "ve", "nha", "doi"
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
            @Value("${cinema.movie-discovery.rerank-candidate-limit:5}") int rerankCandidateLimit
    ) {
        this.movieRepository = movieRepository;
        this.retrievalService = retrievalService;
        this.rerankService = rerankService;
        this.embeddingEnabled = embeddingEnabled;
        this.embeddingModelName = embeddingModelName;
        this.rerankCandidateLimit = Math.max(DEFAULT_RERANK_CANDIDATE_LIMIT, rerankCandidateLimit);
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
        List<String> queryPhrases = buildQueryPhrases(normalizedQuery, queryTokens);
        List<MovieCandidate> candidates = new ArrayList<>();

        for (Movie movie : movies) {
            MovieCandidate candidate = scoreMovie(movie, normalizedQuery, queryTokens, queryPhrases, denseScores);
            if (candidate.score() >= MIN_SCORE || candidate.denseScore() >= 0.62) {
                candidates.add(candidate);
            }
        }

        candidates.sort(Comparator.comparingDouble(MovieCandidate::score).reversed());
        long rerankStartedAt = System.nanoTime();
        List<MovieCandidate> rankedCandidates = rerankCandidates(query, candidates, resolvedLimit);
        long rerankTimeMs = elapsedMs(rerankStartedAt);
        DiscoveryTiming timing = new DiscoveryTiming(
                elapsedMs(requestStartedAt),
                semanticSearchTimeMs,
                rerankTimeMs
        );
        return rankedCandidates.stream()
                .limit(resolvedLimit)
                .map(candidate -> toResponse(candidate, timing))
                .collect(Collectors.toList());
    }

    private MovieCandidate scoreMovie(Movie movie,
                                      String normalizedQuery,
                                      List<String> queryTokens,
                                      List<String> queryPhrases,
                                      Map<Long, Double> denseScores) {
        String title = normalize(movie.getTitle());
        String genre = normalize(movie.getGenre());
        String description = normalize(movie.getDescription());
        String actors = normalize(movie.getActors());
        Set<String> titleTokens = new LinkedHashSet<>(tokenize(title));
        Set<String> genreTokens = new LinkedHashSet<>(tokenize(genre));
        Set<String> descriptionTokens = new LinkedHashSet<>(tokenize(description));
        Set<String> actorTokens = new LinkedHashSet<>(tokenize(actors));
        Set<String> documentTokens = new LinkedHashSet<>();
        documentTokens.addAll(titleTokens);
        documentTokens.addAll(genreTokens);
        documentTokens.addAll(descriptionTokens);
        documentTokens.addAll(actorTokens);

        double score = 0.0;
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        LinkedHashSet<String> signals = new LinkedHashSet<>();

        for (String phrase : queryPhrases) {
            if (phrase.length() < 4) continue;

            if (title.contains(phrase)) {
                score += 36.0;
                reasons.add("Khớp với tên phim hoặc tên nhân vật bạn mô tả");
                signals.add(phrase);
            }
            if (genre.contains(phrase)) {
                score += 22.0;
                reasons.add("Khớp thể loại phim");
                signals.add(phrase);
            }
            if (description.contains(phrase)) {
                score += 20.0;
                reasons.add("Khớp tình huống trong mô tả phim");
                signals.add(phrase);
            }
            if (actors.contains(phrase)) {
                score += 18.0;
                reasons.add("Khớp diễn viên liên quan");
                signals.add(phrase);
            }
        }

        int titleHits = 0;
        int genreHits = 0;
        int descriptionHits = 0;
        int actorHits = 0;
        for (String token : queryTokens) {
            boolean matched = false;
            if (titleTokens.contains(token)) {
                score += 8.0;
                titleHits++;
                matched = true;
            }
            if (genreTokens.contains(token)) {
                score += 5.5;
                genreHits++;
                matched = true;
            }
            if (descriptionTokens.contains(token)) {
                score += 3.2;
                descriptionHits++;
                matched = true;
            }
            if (actorTokens.contains(token)) {
                score += 4.5;
                actorHits++;
                matched = true;
            }
            if (matched && signals.size() < 6) {
                signals.add(token);
            }
        }

        if (titleHits > 0) reasons.add("Có chi tiết khớp với tên phim");
        if (genreHits > 0) reasons.add("Khớp thể loại phim");
        if (descriptionHits > 0) reasons.add("Có nhiều chi tiết gần với nội dung phim");
        if (actorHits > 0) reasons.add("Có diễn viên liên quan");

        score += scoreGenreIntents(normalizedQuery, genre, description, reasons, signals);

        double coverage = queryTokens.isEmpty()
                ? 0.0
                : (double) countTokenCoverage(queryTokens, documentTokens) / queryTokens.size();
        score += coverage * 15.0;

        double denseScore = denseScores.getOrDefault(movie.getMovieId(), 0.0);
        if (denseScore > 0.0) {
            score += denseScore * 34.0;
            if (denseScore >= 0.70) {
                reasons.add("Gợi ý ngữ nghĩa từ nội dung phim");
                signals.add("semantic-model");
            }
        }

        if (movie.getStatus() == Movie.MovieStatus.NOW_SHOWING) {
            score += 2.0;
        } else if (movie.getStatus() == Movie.MovieStatus.SPECIAL_RELEASE) {
            score += 1.0;
        }

        if (reasons.isEmpty() && score > 0) {
            reasons.add("Có chi tiết gần với mô tả của bạn");
        }

        return new MovieCandidate(movie, Math.min(100.0, score), denseScore, null, null, null, List.copyOf(reasons), List.copyOf(signals));
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

        List<MovieCandidate> rerankedWindow = rerankWindow.stream()
                .map(candidate -> applyRerankScore(candidate, rerankResponse))
                .sorted(Comparator.comparingDouble(MovieCandidate::score).reversed())
                .collect(Collectors.toCollection(ArrayList::new));

        if (windowSize < candidates.size()) {
            rerankedWindow.addAll(candidates.subList(windowSize, candidates.size()));
        }
        return rerankedWindow;
    }

    private MovieCandidate applyRerankScore(MovieCandidate candidate,
                                            MovieDiscoveryRerankService.RerankResponse rerankResponse) {
        MovieDiscoveryRerankService.RerankScore rerankScore = rerankResponse.scores().get(candidate.movie().getMovieId());
        if (rerankScore == null) {
            return candidate;
        }

        double combinedScore = combineRerankScore(candidate, rerankScore.score());
        List<String> reasons = new ArrayList<>(candidate.reasons());
        if (rerankScore.reason() != null && !rerankScore.reason().isBlank()) {
            reasons.add(0, rerankScore.reason());
        }

        return new MovieCandidate(
                candidate.movie(),
                combinedScore,
                candidate.denseScore(),
                rerankScore.score(),
                rerankResponse.modelName(),
                rerankScore.reason(),
                List.copyOf(reasons),
                candidate.signals()
        );
    }

    private double combineRerankScore(MovieCandidate candidate, double rerankScore) {
        double rerankPercent = Math.max(0.0, Math.min(1.0, rerankScore)) * 100.0;
        double densePercent = candidate.denseScore() > 0.0 ? candidate.denseScore() * 100.0 : 0.0;
        double baselinePercent = candidate.score();

        double combined = densePercent > 0.0
                ? rerankPercent * 0.65 + densePercent * 0.25 + baselinePercent * 0.10
                : rerankPercent * 0.80 + baselinePercent * 0.20;
        return Math.max(0.0, Math.min(100.0, combined));
    }

    private double scoreGenreIntents(String query,
                                     String genre,
                                     String description,
                                     LinkedHashSet<String> reasons,
                                     LinkedHashSet<String> signals) {
        double score = 0.0;
        Map<String, IntentProfile> profiles = intentProfiles();
        for (Map.Entry<String, IntentProfile> entry : profiles.entrySet()) {
            IntentProfile profile = entry.getValue();
            boolean queryHasIntent = profile.queryTerms().stream().anyMatch(query::contains);
            if (!queryHasIntent) continue;

            boolean movieHasIntent = profile.movieTerms().stream()
                    .anyMatch(term -> genre.contains(term) || description.contains(term));
            if (movieHasIntent) {
                score += profile.weight();
                reasons.add(profile.reason());
                signals.add(entry.getKey());
            }
        }
        return score;
    }

    private Map<String, IntentProfile> intentProfiles() {
        Map<String, IntentProfile> profiles = new HashMap<>();
        profiles.put("sieu anh hung", new IntentProfile(
                List.of("sieu anh hung", "anh hung", "avenger", "nguoi nhen", "batman", "wolverine"),
                List.of("sieu anh hung", "hanh dong", "avenger", "batman", "spider", "wolverine"),
                14.0,
                "Khớp nhóm phim siêu anh hùng"
        ));
        profiles.put("gia dinh", new IntentProfile(
                List.of("gia dinh", "ba", "me", "cha", "con", "ngoai", "noi", "cham soc", "cam dong"),
                List.of("gia dinh", "cham soc", "ba", "me", "cha", "con", "cam dong"),
                12.0,
                "Khớp câu chuyện gia đình"
        ));
        profiles.put("kinh di", new IntentProfile(
                List.of("kinh di", "so", "ma", "quai vat", "sinh vat", "im lang", "am thanh"),
                List.of("kinh di", "giat gan", "sinh vat", "quai vat", "am thanh"),
                12.0,
                "Khớp không khí kinh dị, căng thẳng"
        ));
        profiles.put("tinh cam", new IntentProfile(
                List.of("tinh yeu", "yeu", "lang man", "hen ho", "doi lua", "cam tinh"),
                List.of("tinh cam", "lang man", "tam ly", "tinh yeu"),
                10.0,
                "Khớp sắc thái tình cảm"
        ));
        profiles.put("hai", new IntentProfile(
                List.of("hai", "vui", "cuoi", "vui nhon", "giai tri"),
                List.of("hai", "hoat hinh", "vui", "gia dinh"),
                9.0,
                "Khớp nhu cầu xem phim nhẹ nhàng, giải trí"
        ));
        profiles.put("khoa hoc vien tuong", new IntentProfile(
                List.of("vu tru", "hanh tinh", "ngoai hanh tinh", "da vu tru", "thoi gian", "cong nghe"),
                List.of("khoa hoc vien tuong", "vu tru", "hanh tinh", "da vu tru", "thoi gian"),
                12.0,
                "Khớp bối cảnh khoa học viễn tưởng"
        ));
        profiles.put("toi pham", new IntentProfile(
                List.of("toi pham", "giet nguoi", "tra thu", "tham nhung", "ke thu", "bi mat"),
                List.of("toi pham", "tham nhung", "giet nguoi", "tra thu", "bi mat"),
                10.0,
                "Khớp màu sắc tội phạm, điều tra"
        ));
        return profiles;
    }

    private Map<Long, Double> findDenseScores(String query, List<Movie> movies) {
        try {
            return retrievalService.denseSearchMovies(query, movies).stream()
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

    private List<String> buildQueryPhrases(String normalizedQuery, List<String> queryTokens) {
        LinkedHashSet<String> phrases = new LinkedHashSet<>();
        if (normalizedQuery.length() >= 4 && normalizedQuery.length() <= 80) {
            phrases.add(normalizedQuery);
        }
        for (int size = 4; size >= 2; size--) {
            for (int i = 0; i <= queryTokens.size() - size; i++) {
                phrases.add(String.join(" ", queryTokens.subList(i, i + size)));
            }
        }
        return List.copyOf(phrases);
    }

    private int countTokenCoverage(List<String> queryTokens, Set<String> documentTokens) {
        int count = 0;
        for (String token : queryTokens) {
            if (documentTokens.contains(token)) {
                count++;
            }
        }
        return count;
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

    private record IntentProfile(List<String> queryTerms, List<String> movieTerms, double weight, String reason) {
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
            Double rerankScore,
            String rerankModelName,
            String rerankReason,
            List<String> reasons,
            List<String> signals
    ) {
    }
}
