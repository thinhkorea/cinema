package com.example.cinema.service;

import com.example.cinema.domain.Movie;
import com.example.cinema.domain.Snack;
import com.example.cinema.dto.CinemaBotEmbeddingRebuildResultDTO;
import com.example.cinema.dto.CinemaBotEmbeddingStatusDTO;
import com.example.cinema.repository.MovieRepository;
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

    public CinemaRetrievalService(
            CinemaEmbeddingService embeddingService,
            CinemaSearchDocumentBuilder documentBuilder,
            MovieRepository movieRepository,
            SnackRepository snackRepository
    ) {
        this.embeddingService = embeddingService;
        this.documentBuilder = documentBuilder;
        this.movieRepository = movieRepository;
        this.snackRepository = snackRepository;
    }

    public List<DenseCandidate<Movie>> denseSearchMovies(String query, List<Movie> allMovies) {
        List<Double> queryEmbedding = embeddingService.createEmbedding(query);
        if (queryEmbedding.isEmpty() || allMovies == null || allMovies.isEmpty()) {
            return Collections.emptyList();
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

    @Transactional(readOnly = true)
    public CinemaBotEmbeddingStatusDTO getEmbeddingStatus() {
        List<Movie> movies = movieRepository.findAll();
        List<Snack> snacks = snackRepository.findAll();

        long embeddedMovies = movies.stream()
                .filter(movie -> hasValidEmbedding(movie.getSearchEmbedding()))
                .count();
        long embeddedSnacks = snacks.stream()
                .filter(snack -> hasValidEmbedding(snack.getSearchEmbedding()))
                .count();

        return new CinemaBotEmbeddingStatusDTO(
                embeddingService.isAvailable(),
                movies.size(),
                embeddedMovies,
                movies.size() - embeddedMovies,
                snacks.size(),
                embeddedSnacks,
                snacks.size() - embeddedSnacks
        );
    }

    @Transactional
    public CinemaBotEmbeddingRebuildResultDTO rebuildEmbeddings(boolean force) {
        List<Movie> movies = movieRepository.findAll();
        List<Snack> snacks = snackRepository.findAll();

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

        String message = failedMovies == 0 && failedSnacks == 0
                ? "Embedding cache đã được đồng bộ thành công."
                : "Embedding cache đồng bộ chưa hoàn tất. Kiểm tra Ollama embedding model và thử lại.";
        log.info("[CinemaRetrieval] rebuildEmbeddings force={}, movies updated/failed={}/{}, snacks updated/failed={}/{}",
                force, updatedMovies, failedMovies, updatedSnacks, failedSnacks);

        return new CinemaBotEmbeddingRebuildResultDTO(
                movies.size(),
                updatedMovies,
                failedMovies,
                snacks.size(),
                updatedSnacks,
                failedSnacks,
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
        }
        return embedding;
    }

    private EmbeddingBuildResult rebuildMovieEmbedding(Movie movie, boolean force) {
        if (movie == null) return EmbeddingBuildResult.SKIPPED;
        if (!force && hasValidEmbedding(movie.getSearchEmbedding())) {
            return EmbeddingBuildResult.SKIPPED;
        }

        List<Double> embedding = embeddingService.createEmbedding(documentBuilder.buildMovieSearchDocument(movie));
        if (embedding.isEmpty()) {
            return EmbeddingBuildResult.FAILED;
        }

        movie.setSearchEmbedding(embeddingService.writeEmbedding(embedding));
        movieRepository.save(movie);
        return EmbeddingBuildResult.UPDATED;
    }

    private EmbeddingBuildResult rebuildSnackEmbedding(Snack snack, boolean force) {
        if (snack == null) return EmbeddingBuildResult.SKIPPED;
        if (!force && hasValidEmbedding(snack.getSearchEmbedding())) {
            return EmbeddingBuildResult.SKIPPED;
        }

        List<Double> embedding = embeddingService.createEmbedding(documentBuilder.buildSnackSearchDocument(snack));
        if (embedding.isEmpty()) {
            return EmbeddingBuildResult.FAILED;
        }

        snack.setSearchEmbedding(embeddingService.writeEmbedding(embedding));
        snackRepository.save(snack);
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
        }
        return embedding;
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
