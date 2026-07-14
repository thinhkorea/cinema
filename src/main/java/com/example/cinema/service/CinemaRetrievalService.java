package com.example.cinema.service;

import com.example.cinema.domain.Movie;
import com.example.cinema.domain.SearchDocument;
import com.example.cinema.domain.Snack;
import com.example.cinema.dto.CinemaBotEmbeddingRebuildResultDTO;
import com.example.cinema.dto.CinemaBotEmbeddingStatusDTO;
import com.example.cinema.repository.MovieRepository;
import com.example.cinema.repository.SearchDocumentRepository;
import com.example.cinema.repository.SnackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CinemaRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(CinemaRetrievalService.class);

    private static final int DENSE_RESULT_LIMIT = 8;

    private final CinemaEmbeddingService embeddingService;
    private final CinemaSearchDocumentBuilder documentBuilder;
    private final MovieRepository movieRepository;
    private final SnackRepository snackRepository;
    private final SearchDocumentRepository searchDocumentRepository;
    private final CinemaQdrantService qdrantService;

    public CinemaRetrievalService(
            CinemaEmbeddingService embeddingService,
            CinemaSearchDocumentBuilder documentBuilder,
            MovieRepository movieRepository,
            SnackRepository snackRepository,
            SearchDocumentRepository searchDocumentRepository,
            CinemaQdrantService qdrantService
    ) {
        this.embeddingService = embeddingService;
        this.documentBuilder = documentBuilder;
        this.movieRepository = movieRepository;
        this.snackRepository = snackRepository;
        this.searchDocumentRepository = searchDocumentRepository;
        this.qdrantService = qdrantService;
    }

    public List<DenseCandidate<Movie>> denseSearchMovies(String query, List<Movie> allMovies) {
        List<Double> queryEmbedding = embeddingService.createEmbedding(query);
        if (queryEmbedding.isEmpty() || allMovies == null || allMovies.isEmpty()) {
            return Collections.emptyList();
        }

        List<DenseCandidate<Movie>> qdrantResults = denseSearchMoviesWithQdrant(queryEmbedding, allMovies);
        if (!qdrantResults.isEmpty()) {
            return qdrantResults;
        }

        List<DenseCandidate<Movie>> candidates = new ArrayList<>();
        for (Movie movie : allMovies) {
            List<Double> documentEmbedding = getOrCreateMovieEmbedding(movie);
            double similarity = embeddingService.cosineSimilarity(queryEmbedding, documentEmbedding);
            if (similarity > 0.0) {
                candidates.add(new DenseCandidate<>(movie, embeddingService.normalizeCosineScore(similarity)));
            }
        }

        candidates.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<DenseCandidate<Movie>> results = candidates.stream()
                .limit(DENSE_RESULT_LIMIT)
                .collect(Collectors.toList());
        log.debug("[CinemaRetrieval] denseMovies candidates={}, returned={}", candidates.size(), results.size());
        return results;
    }

    public List<DenseCandidate<Snack>> denseSearchSnacks(String query, List<Snack> allSnacks) {
        List<Double> queryEmbedding = embeddingService.createEmbedding(query);
        if (queryEmbedding.isEmpty() || allSnacks == null || allSnacks.isEmpty()) {
            return Collections.emptyList();
        }

        List<DenseCandidate<Snack>> qdrantResults = denseSearchSnacksWithQdrant(queryEmbedding, allSnacks);
        if (!qdrantResults.isEmpty()) {
            return qdrantResults;
        }

        List<DenseCandidate<Snack>> candidates = new ArrayList<>();
        for (Snack snack : allSnacks) {
            List<Double> documentEmbedding = getOrCreateSnackEmbedding(snack);
            double similarity = embeddingService.cosineSimilarity(queryEmbedding, documentEmbedding);
            if (similarity > 0.0) {
                candidates.add(new DenseCandidate<>(snack, embeddingService.normalizeCosineScore(similarity)));
            }
        }

        candidates.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<DenseCandidate<Snack>> results = candidates.stream()
                .limit(DENSE_RESULT_LIMIT)
                .collect(Collectors.toList());
        log.debug("[CinemaRetrieval] denseSnacks candidates={}, returned={}", candidates.size(), results.size());
        return results;
    }

    public List<Movie> sparseSearchMovies(List<String> keywords, List<Movie> allMovies) {
        if (keywords == null || keywords.isEmpty() || allMovies == null || allMovies.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> documents = allMovies.stream()
                .map(documentBuilder::buildMovieSearchDocument)
                .collect(Collectors.toList());

        List<Movie> results = rankByBm25(allMovies, documents, keywords, 0.60);
        log.debug("[CinemaRetrieval] sparseMovies keywords={}, returned={}", keywords, results.size());
        return results;
    }

    public List<Snack> sparseSearchSnacks(List<String> keywords, List<Snack> allSnacks) {
        if (keywords == null || keywords.isEmpty() || allSnacks == null || allSnacks.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> documents = allSnacks.stream()
                .map(documentBuilder::buildSnackSearchDocument)
                .collect(Collectors.toList());

        List<Snack> results = rankByBm25(allSnacks, documents, keywords, 0.65);
        log.debug("[CinemaRetrieval] sparseSnacks keywords={}, returned={}", keywords, results.size());
        return results;
    }

    public String retrievePolicyContext(String query) {
        List<DenseCandidate<SearchDocument>> candidates = denseSearchPolicyDocuments(query);
        if (candidates.isEmpty()) {
            return null;
        }

        StringBuilder context = new StringBuilder("Tài liệu chính sách liên quan trong hệ thống:\n");
        for (DenseCandidate<SearchDocument> candidate : candidates) {
            SearchDocument document = candidate.item();
            context.append(String.format("- %s (độ phù hợp %.2f):\n%s\n",
                    document.getTitle(),
                    candidate.score(),
                    document.getContent()));
        }
        return context.toString().trim();
    }

    public List<DenseCandidate<SearchDocument>> denseSearchPolicyDocuments(String query) {
        List<Double> queryEmbedding = embeddingService.createEmbedding(query);
        if (queryEmbedding.isEmpty()) {
            return Collections.emptyList();
        }

        List<DenseCandidate<SearchDocument>> qdrantResults = denseSearchPoliciesWithQdrant(queryEmbedding);
        if (!qdrantResults.isEmpty()) {
            return qdrantResults;
        }

        List<SearchDocument> documents = searchDocumentRepository.findBySourceTypeAndActiveTrue(SearchDocument.SourceType.POLICY);
        List<DenseCandidate<SearchDocument>> candidates = new ArrayList<>();
        for (SearchDocument document : documents) {
            List<Double> documentEmbedding = getOrCreateSearchDocumentEmbedding(document);
            double similarity = embeddingService.cosineSimilarity(queryEmbedding, documentEmbedding);
            if (similarity > 0.0) {
                candidates.add(new DenseCandidate<>(document, embeddingService.normalizeCosineScore(similarity)));
            }
        }

        candidates.sort((a, b) -> Double.compare(b.score(), a.score()));
        return candidates.stream()
                .limit(3)
                .collect(Collectors.toList());
    }

    private List<DenseCandidate<Movie>> denseSearchMoviesWithQdrant(List<Double> queryEmbedding, List<Movie> allMovies) {
        Map<Long, Movie> moviesById = allMovies.stream()
                .filter(movie -> movie.getMovieId() != null)
                .collect(Collectors.toMap(Movie::getMovieId, movie -> movie, (left, right) -> left));

        return qdrantService.search("MOVIE", queryEmbedding, DENSE_RESULT_LIMIT).stream()
                .filter(match -> match.sourceId() != null && moviesById.containsKey(match.sourceId()))
                .map(match -> new DenseCandidate<>(moviesById.get(match.sourceId()), match.score()))
                .collect(Collectors.toList());
    }

    private List<DenseCandidate<Snack>> denseSearchSnacksWithQdrant(List<Double> queryEmbedding, List<Snack> allSnacks) {
        Map<Long, Snack> snacksById = allSnacks.stream()
                .filter(snack -> snack.getSnackId() != null)
                .collect(Collectors.toMap(Snack::getSnackId, snack -> snack, (left, right) -> left));

        return qdrantService.search("SNACK", queryEmbedding, DENSE_RESULT_LIMIT).stream()
                .filter(match -> match.sourceId() != null && snacksById.containsKey(match.sourceId()))
                .map(match -> new DenseCandidate<>(snacksById.get(match.sourceId()), match.score()))
                .collect(Collectors.toList());
    }

    private List<DenseCandidate<SearchDocument>> denseSearchPoliciesWithQdrant(List<Double> queryEmbedding) {
        List<SearchDocument> documents = searchDocumentRepository.findBySourceTypeAndActiveTrue(SearchDocument.SourceType.POLICY);
        Map<Long, SearchDocument> documentsById = documents.stream()
                .filter(document -> document.getSearchDocumentId() != null)
                .collect(Collectors.toMap(SearchDocument::getSearchDocumentId, document -> document, (left, right) -> left));

        return qdrantService.search("POLICY", queryEmbedding, 3).stream()
                .filter(match -> match.sourceId() != null && documentsById.containsKey(match.sourceId()))
                .map(match -> new DenseCandidate<>(documentsById.get(match.sourceId()), match.score()))
                .collect(Collectors.toList());
    }

    @Transactional
    public CinemaBotEmbeddingStatusDTO getEmbeddingStatus() {
        List<Movie> movies = movieRepository.findAll();
        List<Snack> snacks = snackRepository.findAll();
        List<SearchDocument> policyDocuments = searchDocumentRepository.findBySourceTypeAndActiveTrue(SearchDocument.SourceType.POLICY);

        long embeddedMovies = movies.stream()
                .filter(movie -> hasValidEmbedding(movie.getSearchEmbedding()))
                .count();
        long embeddedSnacks = snacks.stream()
                .filter(snack -> hasValidEmbedding(snack.getSearchEmbedding()))
                .count();
        long embeddedPolicyDocuments = policyDocuments.stream()
                .filter(document -> hasValidEmbedding(document.getSearchEmbedding()))
                .count();

        return new CinemaBotEmbeddingStatusDTO(
                embeddingService.isAvailable(),
                qdrantService.isAvailable(),
                movies.size(),
                embeddedMovies,
                movies.size() - embeddedMovies,
                qdrantService.countBySourceType("MOVIE"),
                snacks.size(),
                embeddedSnacks,
                snacks.size() - embeddedSnacks,
                qdrantService.countBySourceType("SNACK"),
                policyDocuments.size(),
                embeddedPolicyDocuments,
                policyDocuments.size() - embeddedPolicyDocuments,
                qdrantService.countBySourceType("POLICY")
        );
    }

    @Transactional
    public CinemaBotEmbeddingRebuildResultDTO rebuildEmbeddings(boolean force) {
        List<Movie> movies = movieRepository.findAll();
        List<Snack> snacks = snackRepository.findAll();
        List<SearchDocument> policyDocuments = searchDocumentRepository.findBySourceTypeAndActiveTrue(SearchDocument.SourceType.POLICY);

        int updatedMovies = 0;
        int failedMovies = 0;
        for (Movie movie : movies) {
            EmbeddingBuildResult result = rebuildMovieEmbedding(movie, force);
            if (result == EmbeddingBuildResult.UPDATED) {
                updatedMovies++;
            } else if (result == EmbeddingBuildResult.FAILED) {
                failedMovies++;
            }
        }

        int updatedSnacks = 0;
        int failedSnacks = 0;
        for (Snack snack : snacks) {
            EmbeddingBuildResult result = rebuildSnackEmbedding(snack, force);
            if (result == EmbeddingBuildResult.UPDATED) {
                updatedSnacks++;
            } else if (result == EmbeddingBuildResult.FAILED) {
                failedSnacks++;
            }
        }
        int updatedPolicyDocuments = 0;
        int failedPolicyDocuments = 0;
        for (SearchDocument document : policyDocuments) {
            EmbeddingBuildResult result = rebuildSearchDocumentEmbedding(document, force);
            if (result == EmbeddingBuildResult.UPDATED) {
                updatedPolicyDocuments++;
            } else if (result == EmbeddingBuildResult.FAILED) {
                failedPolicyDocuments++;
            }
        }

        String message = failedMovies == 0 && failedSnacks == 0 && failedPolicyDocuments == 0
                ? "Embedding cache đã được đồng bộ thành công."
                : "Embedding cache đồng bộ chưa hoàn tất. Kiểm tra Ollama embedding model và thử lại.";
        log.info("[CinemaRetrieval] rebuildEmbeddings force={}, movies updated/failed={}/{}, snacks updated/failed={}/{}, policies updated/failed={}/{}",
                force, updatedMovies, failedMovies, updatedSnacks, failedSnacks, updatedPolicyDocuments, failedPolicyDocuments);

        return new CinemaBotEmbeddingRebuildResultDTO(
                movies.size(),
                updatedMovies,
                failedMovies,
                snacks.size(),
                updatedSnacks,
                failedSnacks,
                policyDocuments.size(),
                updatedPolicyDocuments,
                failedPolicyDocuments,
                message
        );
    }

    private List<Double> getOrCreateMovieEmbedding(Movie movie) {
        if (movie == null) return Collections.emptyList();

        List<Double> cachedEmbedding = embeddingService.readEmbedding(movie.getSearchEmbedding());
        if (!cachedEmbedding.isEmpty()) return cachedEmbedding;

        List<Double> embedding = embeddingService.createEmbedding(documentBuilder.buildMovieSearchDocument(movie));
        if (!embedding.isEmpty()) {
            movie.setSearchEmbedding(embeddingService.writeEmbedding(embedding));
            movieRepository.save(movie);
            upsertMovieToQdrant(movie, embedding);
        }
        return embedding;
    }

    private EmbeddingBuildResult rebuildMovieEmbedding(Movie movie, boolean force) {
        if (movie == null) return EmbeddingBuildResult.SKIPPED;
        List<Double> cachedEmbedding = embeddingService.readEmbedding(movie.getSearchEmbedding());
        if (!force && !cachedEmbedding.isEmpty()) {
            upsertMovieToQdrant(movie, cachedEmbedding);
            return EmbeddingBuildResult.SKIPPED;
        }

        List<Double> embedding = embeddingService.createEmbedding(documentBuilder.buildMovieSearchDocument(movie));
        if (embedding.isEmpty()) {
            return EmbeddingBuildResult.FAILED;
        }

        movie.setSearchEmbedding(embeddingService.writeEmbedding(embedding));
        movieRepository.save(movie);
        upsertMovieToQdrant(movie, embedding);
        return EmbeddingBuildResult.UPDATED;
    }

    private EmbeddingBuildResult rebuildSnackEmbedding(Snack snack, boolean force) {
        if (snack == null) return EmbeddingBuildResult.SKIPPED;
        List<Double> cachedEmbedding = embeddingService.readEmbedding(snack.getSearchEmbedding());
        if (!force && !cachedEmbedding.isEmpty()) {
            upsertSnackToQdrant(snack, cachedEmbedding);
            return EmbeddingBuildResult.SKIPPED;
        }

        List<Double> embedding = embeddingService.createEmbedding(documentBuilder.buildSnackSearchDocument(snack));
        if (embedding.isEmpty()) {
            return EmbeddingBuildResult.FAILED;
        }

        snack.setSearchEmbedding(embeddingService.writeEmbedding(embedding));
        snackRepository.save(snack);
        upsertSnackToQdrant(snack, embedding);
        return EmbeddingBuildResult.UPDATED;
    }

    private boolean hasValidEmbedding(String embeddingJson) {
        return !embeddingService.readEmbedding(embeddingJson).isEmpty();
    }

    private List<Double> getOrCreateSnackEmbedding(Snack snack) {
        if (snack == null) return Collections.emptyList();

        List<Double> cachedEmbedding = embeddingService.readEmbedding(snack.getSearchEmbedding());
        if (!cachedEmbedding.isEmpty()) return cachedEmbedding;

        List<Double> embedding = embeddingService.createEmbedding(documentBuilder.buildSnackSearchDocument(snack));
        if (!embedding.isEmpty()) {
            snack.setSearchEmbedding(embeddingService.writeEmbedding(embedding));
            snackRepository.save(snack);
            upsertSnackToQdrant(snack, embedding);
        }
        return embedding;
    }

    private List<Double> getOrCreateSearchDocumentEmbedding(SearchDocument document) {
        if (document == null) return Collections.emptyList();

        List<Double> cachedEmbedding = embeddingService.readEmbedding(document.getSearchEmbedding());
        if (!cachedEmbedding.isEmpty()) return cachedEmbedding;

        List<Double> embedding = embeddingService.createEmbedding(document.getContent());
        if (!embedding.isEmpty()) {
            document.setSearchEmbedding(embeddingService.writeEmbedding(embedding));
            searchDocumentRepository.save(document);
            upsertSearchDocumentToQdrant(document, embedding);
        }
        return embedding;
    }

    private EmbeddingBuildResult rebuildSearchDocumentEmbedding(SearchDocument document, boolean force) {
        if (document == null) return EmbeddingBuildResult.SKIPPED;
        List<Double> cachedEmbedding = embeddingService.readEmbedding(document.getSearchEmbedding());
        if (!force && !cachedEmbedding.isEmpty()) {
            upsertSearchDocumentToQdrant(document, cachedEmbedding);
            return EmbeddingBuildResult.SKIPPED;
        }

        List<Double> embedding = embeddingService.createEmbedding(document.getContent());
        if (embedding.isEmpty()) {
            return EmbeddingBuildResult.FAILED;
        }

        document.setSearchEmbedding(embeddingService.writeEmbedding(embedding));
        searchDocumentRepository.save(document);
        upsertSearchDocumentToQdrant(document, embedding);
        return EmbeddingBuildResult.UPDATED;
    }

    private void upsertMovieToQdrant(Movie movie, List<Double> embedding) {
        if (movie == null || movie.getMovieId() == null) return;
        qdrantService.upsertDocument(
                "MOVIE",
                movie.getMovieId(),
                "MOVIE:" + movie.getMovieId(),
                movie.getTitle(),
                documentBuilder.buildMovieSearchDocument(movie),
                embedding
        );
    }

    private void upsertSnackToQdrant(Snack snack, List<Double> embedding) {
        if (snack == null || snack.getSnackId() == null) return;
        qdrantService.upsertDocument(
                "SNACK",
                snack.getSnackId(),
                "SNACK:" + snack.getSnackId(),
                snack.getSnackName(),
                documentBuilder.buildSnackSearchDocument(snack),
                embedding
        );
    }

    private void upsertSearchDocumentToQdrant(SearchDocument document, List<Double> embedding) {
        if (document == null || document.getSearchDocumentId() == null) return;
        qdrantService.upsertDocument(
                document.getSourceType() != null ? document.getSourceType().name() : "POLICY",
                document.getSearchDocumentId(),
                document.getSourceKey(),
                document.getTitle(),
                document.getContent(),
                embedding
        );
    }

     private <T> List<T> rankByBm25(List<T> items, List<String> rawDocuments, List<String> keywords, double fuzzyThreshold) {
        List<String> queryTokens = normalizeQueryTokens(keywords);
        if (queryTokens.isEmpty()) return Collections.emptyList();

        List<List<String>> tokenizedDocuments = rawDocuments.stream()
                .map(this::tokenizeSearchText)
                .collect(Collectors.toList());
        Map<String, Integer> documentFrequency = buildDocumentFrequency(tokenizedDocuments);
        double averageDocumentLength = tokenizedDocuments.stream()
                .mapToInt(List::size)
                .average()
                .orElse(0.0);

        List<Bm25Candidate<T>> candidates = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            List<String> documentTokens = tokenizedDocuments.get(i);
            if (documentTokens.isEmpty()) continue;

            String normalizedDocument = normalizeSearchText(rawDocuments.get(i));
            double bm25Score = calculateBm25Score(
                    queryTokens,
                    documentTokens,
                    documentFrequency,
                    averageDocumentLength,
                    tokenizedDocuments.size()
            );
            double fallbackScore = calculateKeywordFallbackScore(
                    normalizedDocument,
                    documentTokens,
                    keywords,
                    fuzzyThreshold
            );
            double totalScore = bm25Score + fallbackScore;
            if (totalScore > 0.0) {
                candidates.add(new Bm25Candidate<>(items.get(i), totalScore));
            }
        }

        candidates.sort((a, b) -> Double.compare(b.score(), a.score()));
        return candidates.stream()
                .map(Bm25Candidate::item)
                .collect(Collectors.toList());
    }

    private List<String> normalizeQueryTokens(List<String> keywords) {
        if (keywords == null) return Collections.emptyList();
        return keywords.stream()
                .filter(Objects::nonNull)
                .flatMap(keyword -> tokenizeSearchText(keyword).stream())
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> tokenizeSearchText(String value) {
        String normalized = normalizeSearchText(value);
        if (normalized.isEmpty()) return Collections.emptyList();

        return Arrays.stream(normalized.split("[^a-z0-9]+"))
                .filter(token -> !token.isBlank())
                .filter(token -> token.length() > 1 || token.chars().allMatch(Character::isDigit))
                .collect(Collectors.toList());
    }

    private String normalizeSearchText(String value) {
        if (value == null) return "";
        String normalized = removeAccents(value.toLowerCase(Locale.ROOT));
        if (normalized == null) return "";
        return normalized
                .replace('\u0111', 'd')
                .replace('\u0110', 'd')
                .trim();
    }

    private Map<String, Integer> buildDocumentFrequency(List<List<String>> tokenizedDocuments) {
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (List<String> documentTokens : tokenizedDocuments) {
            Set<String> uniqueTokens = new HashSet<>(documentTokens);
            for (String token : uniqueTokens) {
                documentFrequency.merge(token, 1, Integer::sum);
            }
        }
        return documentFrequency;
    }

    private double calculateBm25Score(List<String> queryTokens,
                                      List<String> documentTokens,
                                      Map<String, Integer> documentFrequency,
                                      double averageDocumentLength,
                                      int totalDocuments) {
        if (queryTokens.isEmpty() || documentTokens.isEmpty() || totalDocuments <= 0 || averageDocumentLength <= 0) {
            return 0.0;
        }

        Map<String, Integer> termFrequency = new HashMap<>();
        for (String token : documentTokens) {
            termFrequency.merge(token, 1, Integer::sum);
        }

        final double k1 = 1.2;
        final double b = 0.75;
        double score = 0.0;

        for (String queryToken : queryTokens) {
            int tf = termFrequency.getOrDefault(queryToken, 0);
            if (tf == 0) continue;

            int df = documentFrequency.getOrDefault(queryToken, 0);
            double idf = Math.log(1.0 + (totalDocuments - df + 0.5) / (df + 0.5));
            double lengthNormalization = 1.0 - b + b * (documentTokens.size() / averageDocumentLength);
            score += idf * ((tf * (k1 + 1.0)) / (tf + k1 * lengthNormalization));
        }

        return score;
    }

    private double calculateKeywordFallbackScore(String normalizedDocument,
                                                 List<String> documentTokens,
                                                 List<String> keywords,
                                                 double fuzzyThreshold) {
        double score = 0.0;
        for (String keyword : keywords) {
            String normalizedKeyword = normalizeSearchText(keyword);
            if (normalizedKeyword.isEmpty()) continue;

            if (normalizedDocument.contains(normalizedKeyword)) {
                score = Math.max(score, 2.0);
                continue;
            }

            for (String queryToken : tokenizeSearchText(keyword)) {
                if (documentTokens.contains(queryToken)) {
                    score = Math.max(score, 1.0);
                    continue;
                }
                for (String documentToken : documentTokens) {
                    if (getSimilarity(documentToken, queryToken) >= fuzzyThreshold) {
                        score = Math.max(score, 0.75);
                        break;
                    }
                }
            }
        }
        return score;
    }

    private String removeAccents(String src) {
        if (src == null) return null;
        String normalized = Normalizer.normalize(src, Normalizer.Form.NFD);
        return Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
                .matcher(normalized)
                .replaceAll("")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D');
    }

    private int getLevenshteinDistance(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return Integer.MAX_VALUE;
        }
        int len1 = s1.length();
        int len2 = s2.length();

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= len2; j++) {
                char c2 = s2.charAt(j - 1);
                if (c1 == c2) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }
        return dp[len1][len2];
    }

    private double getSimilarity(String s1, String s2) {
        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) return 1.0;
        return 1.0 - ((double) getLevenshteinDistance(s1, s2) / maxLength);
    }

    private enum EmbeddingBuildResult {
        UPDATED,
        SKIPPED,
        FAILED
    }

    public record DenseCandidate<T>(T item, double score) {
    }

    private record Bm25Candidate<T>(T item, double score) {
    }
}
