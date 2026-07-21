package com.example.cinema.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CinemaEmbeddingService {

    private static final int EMBEDDING_DOCUMENT_VERSION = 4;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String ollamaEmbeddingUrl;
    private final String embeddingModelName;

    public CinemaEmbeddingService(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${cinema.bot.embedding-url:http://localhost:11434/api/embeddings}") String ollamaEmbeddingUrl,
            @Value("${cinema.bot.embedding-model:nomic-embed-text}") String embeddingModelName,
            @Value("${cinema.bot.embedding-connect-timeout-seconds:10}") int connectTimeoutSeconds,
            @Value("${cinema.bot.embedding-read-timeout-seconds:180}") int readTimeoutSeconds
    ) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .setReadTimeout(Duration.ofSeconds(readTimeoutSeconds))
                .build();
        this.ollamaEmbeddingUrl = ollamaEmbeddingUrl;
        this.embeddingModelName = embeddingModelName;
    }

    public List<Double> createEmbedding(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> request = new HashMap<>();
            request.put("model", embeddingModelName);
            request.put("prompt", text);

            Map<?, ?> response = restTemplate.postForObject(
                    ollamaEmbeddingUrl,
                    new HttpEntity<>(request, headers),
                    Map.class
            );
            if (response == null) return Collections.emptyList();

            Object embeddingValue = response.get("embedding");
            if (!(embeddingValue instanceof List<?> values)) {
                return Collections.emptyList();
            }

            List<Double> embedding = new ArrayList<>();
            for (Object value : values) {
                if (value instanceof Number number) {
                    embedding.add(number.doubleValue());
                }
            }
            return embedding;
        } catch (RestClientException ex) {
            return Collections.emptyList();
        }
    }

    public boolean isAvailable() {
        return !createEmbedding("cinema bot embedding health check").isEmpty();
    }

    public List<Double> readEmbedding(String embeddingJson) {
        if (embeddingJson == null || embeddingJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            JsonNode root = objectMapper.readTree(embeddingJson);
            if (!root.isObject() || root.path("version").asInt(-1) != EMBEDDING_DOCUMENT_VERSION) {
                return Collections.emptyList();
            }
            String cachedModel = root.path("model").asText("");
            if (!embeddingModelName.equals(cachedModel)) {
                return Collections.emptyList();
            }

            JsonNode valuesNode = root.path("values");
            if (!valuesNode.isArray()) {
                return Collections.emptyList();
            }

            List<Double> embedding = new ArrayList<>();
            for (JsonNode value : valuesNode) {
                if (value.isNumber()) {
                    embedding.add(value.asDouble());
                }
            }
            return embedding;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    public String writeEmbedding(List<Double> embedding) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("version", EMBEDDING_DOCUMENT_VERSION);
            payload.put("model", embeddingModelName);
            payload.put("values", embedding);
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return null;
        }
    }

    public double cosineSimilarity(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty() || left.size() != right.size()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double leftMagnitude = 0.0;
        double rightMagnitude = 0.0;
        for (int i = 0; i < left.size(); i++) {
            double leftValue = left.get(i);
            double rightValue = right.get(i);
            dotProduct += leftValue * rightValue;
            leftMagnitude += leftValue * leftValue;
            rightMagnitude += rightValue * rightValue;
        }

        if (leftMagnitude == 0.0 || rightMagnitude == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
    }

    public double normalizeCosineScore(double similarity) {
        return Math.max(0.0, Math.min(1.0, (similarity + 1.0) / 2.0));
    }
}
