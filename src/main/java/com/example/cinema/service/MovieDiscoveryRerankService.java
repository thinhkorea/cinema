package com.example.cinema.service;

import com.example.cinema.domain.Movie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MovieDiscoveryRerankService {

    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final String rerankUrl;
    private final String rerankModelName;

    public MovieDiscoveryRerankService(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${cinema.movie-discovery.rerank-enabled:true}") boolean enabled,
            @Value("${cinema.movie-discovery.rerank-url:http://localhost:8002/rerank-movies}") String rerankUrl,
            @Value("${cinema.movie-discovery.rerank-model:BAAI/bge-reranker-v2-m3}") String rerankModelName,
            @Value("${cinema.movie-discovery.rerank-connect-timeout-seconds:10}") int connectTimeoutSeconds,
            @Value("${cinema.movie-discovery.rerank-read-timeout-seconds:180}") int readTimeoutSeconds
    ) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .setReadTimeout(Duration.ofSeconds(readTimeoutSeconds))
                .build();
        this.enabled = enabled;
        this.rerankUrl = rerankUrl;
        this.rerankModelName = rerankModelName;
    }

    public RerankResponse rerank(String query, List<RerankCandidate> candidates) {
        if (!enabled || query == null || query.isBlank() || candidates == null || candidates.isEmpty()) {
            return RerankResponse.empty();
        }

        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("query", query);
            request.put("model", rerankModelName);
            request.put("candidates", candidates.stream()
                    .map(this::toPayload)
                    .collect(Collectors.toList()));

            Map<?, ?> response = restTemplate.postForObject(
                    rerankUrl,
                    new HttpEntity<>(request, jsonHeaders()),
                    Map.class
            );
            return parseResponse(response);
        } catch (RestClientException ex) {
            return RerankResponse.empty();
        }
    }

    private Map<String, Object> toPayload(RerankCandidate candidate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("movieId", candidate.movieId());
        payload.put("title", candidate.title());
        payload.put("genre", candidate.genre());
        payload.put("description", candidate.description());
        payload.put("actors", candidate.actors());
        payload.put("score", candidate.score());
        payload.put("semanticScore", candidate.semanticScore());
        payload.put("document", buildRerankDocument(candidate));
        return payload;
    }

    private String buildRerankDocument(RerankCandidate candidate) {
        return String.join("\n",
                safe(candidate.title()),
                safe(candidate.genre()),
                safe(candidate.description()),
                safe(candidate.actors())
        ).trim();
    }

    private RerankResponse parseResponse(Map<?, ?> response) {
        if (response == null) {
            return RerankResponse.empty();
        }

        Object resultsValue = response.get("results");
        if (!(resultsValue instanceof List<?> results)) {
            return RerankResponse.empty();
        }

        String modelName = firstText(response.get("modelName"), response.get("model"), rerankModelName);
        Map<Long, RerankScore> scores = new HashMap<>();
        for (Object item : results) {
            if (!(item instanceof Map<?, ?> result)) continue;

            Long movieId = asLong(firstValue(result, "movieId", "id"));
            Double score = asDouble(firstValue(result, "rerankScore", "score"));
            if (movieId == null || score == null) continue;

            String reason = firstText(result.get("reason"), result.get("matchReason"), null);
            scores.put(movieId, new RerankScore(normalizeScore(score), reason));
        }

        if (scores.isEmpty()) {
            return RerankResponse.empty();
        }
        return new RerankResponse(modelName, scores);
    }

    private Object firstValue(Map<?, ?> map, String firstKey, String secondKey) {
        Object value = map.get(firstKey);
        return value != null ? value : map.get(secondKey);
    }

    private String firstText(Object firstValue, Object secondValue, String fallback) {
        if (firstValue != null && !firstValue.toString().isBlank()) {
            return firstValue.toString();
        }
        if (secondValue != null && !secondValue.toString().isBlank()) {
            return secondValue.toString();
        }
        return fallback;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private double normalizeScore(double score) {
        double normalized = score > 1.0 ? score / 100.0 : score;
        return Math.max(0.0, Math.min(1.0, normalized));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    public record RerankCandidate(
            Long movieId,
            String title,
            String genre,
            String description,
            String actors,
            double score,
            Double semanticScore
    ) {
        public static RerankCandidate from(Movie movie, double score, double semanticScore) {
            return new RerankCandidate(
                    movie.getMovieId(),
                    movie.getTitle(),
                    movie.getGenre(),
                    movie.getDescription(),
                    movie.getActors(),
                    score,
                    semanticScore > 0.0 ? semanticScore : null
            );
        }
    }

    public record RerankScore(double score, String reason) {
    }

    public record RerankResponse(String modelName, Map<Long, RerankScore> scores) {
        public static RerankResponse empty() {
            return new RerankResponse(null, Collections.emptyMap());
        }

        public boolean hasScores() {
            return scores != null && !scores.isEmpty();
        }
    }
}
