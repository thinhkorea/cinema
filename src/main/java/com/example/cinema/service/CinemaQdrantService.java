package com.example.cinema.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CinemaQdrantService {

    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final String qdrantUrl;
    private final String collectionName;

    public CinemaQdrantService(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${cinema.bot.qdrant.enabled:true}") boolean enabled,
            @Value("${cinema.bot.qdrant.url:http://localhost:6333}") String qdrantUrl,
            @Value("${cinema.bot.qdrant.collection:cinema_bot_documents}") String collectionName
    ) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
        this.enabled = enabled;
        this.qdrantUrl = trimTrailingSlash(qdrantUrl);
        this.collectionName = collectionName;
    }

    public boolean isAvailable() {
        if (!enabled) return false;
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(qdrantUrl + "/collections", Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException ex) {
            return false;
        }
    }

    public boolean upsertDocument(String sourceType, Long sourceId, String sourceKey, String title, String content, List<Double> embedding) {
        if (!enabled || sourceType == null || embedding == null || embedding.isEmpty()) {
            return false;
        }
        if (!ensureCollection(embedding.size())) {
            return false;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceType", sourceType);
        payload.put("sourceId", sourceId);
        payload.put("sourceKey", sourceKey);
        payload.put("title", title);
        payload.put("content", content);

        Map<String, Object> point = new LinkedHashMap<>();
        point.put("id", stablePointId(sourceType, sourceKey != null ? sourceKey : String.valueOf(sourceId)));
        point.put("vector", embedding);
        point.put("payload", payload);

        Map<String, Object> request = Map.of("points", List.of(point));

        try {
            restTemplate.exchange(
                    qdrantUrl + "/collections/" + collectionName + "/points?wait=true",
                    HttpMethod.PUT,
                    new HttpEntity<>(request, jsonHeaders()),
                    Map.class
            );
            return true;
        } catch (RestClientException ex) {
            return false;
        }
    }

    public List<QdrantMatch> search(String sourceType, List<Double> queryEmbedding, int limit) {
        if (!enabled || sourceType == null || queryEmbedding == null || queryEmbedding.isEmpty()) {
            return List.of();
        }

        Map<String, Object> match = Map.of("value", sourceType);
        Map<String, Object> must = Map.of("key", "sourceType", "match", match);
        Map<String, Object> filter = Map.of("must", List.of(must));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("vector", queryEmbedding);
        request.put("limit", limit);
        request.put("with_payload", true);
        request.put("filter", filter);

        try {
            Map<?, ?> response = restTemplate.postForObject(
                    qdrantUrl + "/collections/" + collectionName + "/points/search",
                    new HttpEntity<>(request, jsonHeaders()),
                    Map.class
            );
            return parseMatches(response);
        } catch (RestClientException ex) {
            return List.of();
        }
    }

    public long countBySourceType(String sourceType) {
        if (!enabled || sourceType == null || sourceType.isBlank()) {
            return 0L;
        }

        Map<String, Object> match = Map.of("value", sourceType);
        Map<String, Object> must = Map.of("key", "sourceType", "match", match);
        Map<String, Object> filter = Map.of("must", List.of(must));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("exact", true);
        request.put("filter", filter);

        try {
            Map<?, ?> response = restTemplate.postForObject(
                    qdrantUrl + "/collections/" + collectionName + "/points/count",
                    new HttpEntity<>(request, jsonHeaders()),
                    Map.class
            );
            return parseCount(response);
        } catch (RestClientException ex) {
            return 0L;
        }
    }

    private boolean ensureCollection(int vectorSize) {
        if (collectionExists()) {
            return true;
        }

        Map<String, Object> vectors = new LinkedHashMap<>();
        vectors.put("size", vectorSize);
        vectors.put("distance", "Cosine");
        Map<String, Object> request = Map.of("vectors", vectors);

        try {
            restTemplate.exchange(
                    qdrantUrl + "/collections/" + collectionName,
                    HttpMethod.PUT,
                    new HttpEntity<>(request, jsonHeaders()),
                    Map.class
            );
            return true;
        } catch (RestClientException ex) {
            return false;
        }
    }

    private boolean collectionExists() {
        if (!enabled) return false;
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(qdrantUrl + "/collections/" + collectionName, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException ex) {
            return false;
        }
    }

    private List<QdrantMatch> parseMatches(Map<?, ?> response) {
        if (response == null || !(response.get("result") instanceof List<?> results)) {
            return List.of();
        }

        List<QdrantMatch> matches = new ArrayList<>();
        for (Object item : results) {
            if (!(item instanceof Map<?, ?> point)) continue;
            Object payloadValue = point.get("payload");
            if (!(payloadValue instanceof Map<?, ?> payload)) continue;
            Long sourceId = asLong(payload.get("sourceId"));
            String sourceKey = payload.get("sourceKey") != null ? payload.get("sourceKey").toString() : null;
            double score = point.get("score") instanceof Number number ? number.doubleValue() : 0.0;
            matches.add(new QdrantMatch(sourceId, sourceKey, score));
        }
        return matches;
    }

    private long parseCount(Map<?, ?> response) {
        if (response == null || !(response.get("result") instanceof Map<?, ?> result)) {
            return 0L;
        }
        Object count = result.get("count");
        return count instanceof Number number ? number.longValue() : 0L;
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

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String stablePointId(String sourceType, String sourceKey) {
        String raw = sourceType + ":" + sourceKey;
        return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "http://localhost:6333";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record QdrantMatch(Long sourceId, String sourceKey, double score) {
    }
}
