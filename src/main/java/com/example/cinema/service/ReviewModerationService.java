package com.example.cinema.service;

import com.example.cinema.dto.OllamaChatRequestDTO;
import com.example.cinema.dto.OllamaChatResponseDTO;
import com.example.cinema.dto.OllamaMessageDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.text.Normalizer;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ReviewModerationService {

    private static final String PROVIDER_RULE = "rule";
    private static final String PROVIDER_HTTP_AI = "http-ai";
    private static final String PROVIDER_OLLAMA = "ollama";

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)(https?://|www\\.|\\b[a-z0-9-]+\\.(com|net|org|vn|xyz|shop|info)\\b)");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(0|\\+84)\\d{8,10}(?!\\d)");
    private static final Pattern REPEATED_CHAR_PATTERN = Pattern.compile("([a-z0-9])\\1{5,}");
    private static final Set<String> PROFANE_TOKENS = Set.of(
            "dit", "djt", "deo", "dm", "dmm", "dcm", "vcl", "vl", "clgt", "cmm", "cc");
    private static final Set<String> PROFANE_COMPACT_PHRASES = Set.of(
            "ditme", "duoma", "duma", "dume", "dmm", "dcm", "clgt");
    private static final Set<String> ACCENTED_PROFANITY = Set.of(
            "địt", "đụ", "đéo", "đĩ", "lồn", "cặc", "buồi", "đụ má", "địt mẹ");
    private static final Set<String> SPAM_PHRASES = Set.of(
            "casino", "tai xiu", "ca cuoc", "lo de", "telegram", "zalo", "vay tien",
            "lai suat", "kiem tien", "free spin", "nap tien", "link vao");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String moderationProvider;
    private final String httpModerationUrl;
    private final String ollamaModerationUrl;
    private final String ollamaModerationModel;

    public ReviewModerationService(
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper,
            @Value("${review.moderation.provider:rule}") String moderationProvider,
            @Value("${review.moderation.http-url:http://localhost:8001/moderate-review}") String httpModerationUrl,
            @Value("${review.moderation.ollama-url:${cinema.bot.ollama-url:http://localhost:11434/api/chat}}") String ollamaModerationUrl,
            @Value("${review.moderation.ollama-model:${cinema.bot.model:cinema-bot}}") String ollamaModerationModel) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(4))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
        this.objectMapper = objectMapper;
        this.moderationProvider = moderationProvider == null ? PROVIDER_RULE : moderationProvider.trim().toLowerCase(Locale.ROOT);
        this.httpModerationUrl = httpModerationUrl;
        this.ollamaModerationUrl = ollamaModerationUrl;
        this.ollamaModerationModel = ollamaModerationModel;
    }

    public ModerationResult moderateMovieReview(String comment) {
        if (comment == null || comment.isBlank()) {
            return ModerationResult.approved("LOCAL_RULES_FALLBACK", "Bình luận rỗng hoặc không có nội dung cần kiểm duyệt.");
        }

        if (PROVIDER_HTTP_AI.equals(moderationProvider)) {
            ModerationResult httpResult = callHttpAiModerator(comment);
            if (httpResult != null) {
                return httpResult;
            }
        } else if (PROVIDER_OLLAMA.equals(moderationProvider)) {
            ModerationResult ollamaResult = callOllamaModerator(comment);
            if (ollamaResult != null) {
                return ollamaResult;
            }
        }

        return localFallbackModeration(comment);
    }

    public boolean looksUnsafeByRules(String comment) {
        return localFallbackModeration(comment).flagged();
    }

    private ModerationResult callHttpAiModerator(String comment) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> request = new HashMap<>();
            request.put("text", comment);
            request.put("source", "MOVIE_REVIEW");

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    httpModerationUrl,
                    new HttpEntity<>(request, headers),
                    Map.class
            );

            return response == null ? null : parseStructuredResult(response, "HTTP_AI");
        } catch (RestClientException | IllegalArgumentException ex) {
            return null;
        }
    }

    private ModerationResult callOllamaModerator(String comment) {
        try {
            OllamaChatRequestDTO request = new OllamaChatRequestDTO(
                    ollamaModerationModel,
                    List.of(new OllamaMessageDTO("user", buildModerationPrompt(comment))),
                    false
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            OllamaChatResponseDTO response = restTemplate.postForObject(
                    ollamaModerationUrl,
                    new HttpEntity<>(request, headers),
                    OllamaChatResponseDTO.class
            );

            String content = response != null ? response.content() : null;
            if (content == null || content.isBlank()) {
                return null;
            }

            return parseOllamaResult(content);
        } catch (RestClientException | IllegalArgumentException ex) {
            return null;
        }
    }

    private String buildModerationPrompt(String comment) {
        return """
                Bạn là bộ kiểm duyệt bình luận đánh giá phim cho rạp chiếu phim.
                Hãy phân loại bình luận tiếng Việt sau đây.

                Gắn flagged=true nếu nội dung có một trong các dấu hiệu:
                - spam, quảng cáo, link, số điện thoại, lôi kéo qua nền tảng khác
                - tục tĩu, chửi bậy, xúc phạm, quấy rối
                - ngôn từ thù ghét, phân biệt, đe dọa
                - nội dung không liên quan được lặp lại nhiều lần

                Chỉ trả về JSON hợp lệ, không markdown, không giải thích ngoài JSON.
                Schema:
                {
                  "flagged": true/false,
                  "violationType": "NONE|SPAM|PROFANITY|HARASSMENT|HATE|THREAT|SCAM|OTHER",
                  "severity": "NONE|LOW|MEDIUM|HIGH",
                  "confidence": 0.0,
                  "reason": "lý do ngắn bằng tiếng Việt"
                }

                Bình luận:
                "%s"
                """.formatted(comment.replace("\"", "\\\""));
    }

    private ModerationResult parseOllamaResult(String rawContent) {
        String json = extractJsonObject(rawContent);
        Map<String, Object> data;
        try {
            data = objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException("AI moderation response is not valid JSON", ex);
        }

        return parseStructuredResult(data, "OLLAMA_AI");
    }

    private ModerationResult parseStructuredResult(Map<String, Object> data, String provider) {
        String label = normalizeTextValue(firstValue(data, "violationType", "category", "label", "type"), "NONE");
        boolean flagged = readBooleanFlag(firstValue(data, "flagged", "unsafe", "isUnsafe", "toxic")) || isUnsafeLabel(label);
        String violationType = flagged ? normalizeViolationType(label) : "NONE";
        String severity = normalizeTextValue(firstValue(data, "severity", "level"), flagged ? "MEDIUM" : "NONE");
        String reason = normalizeTextValue(data.get("reason"), flagged ? "AI phát hiện nội dung không phù hợp." : "AI không phát hiện vi phạm.");
        double confidence = readDouble(firstValue(data, "confidence", "score", "probability"));

        if (flagged && "NONE".equalsIgnoreCase(severity)) {
            severity = "MEDIUM";
        }

        return new ModerationResult(flagged, violationType, severity.toUpperCase(Locale.ROOT), confidence, reason, provider);
    }

    private String extractJsonObject(String rawContent) {
        String trimmed = rawContent.trim()
                .replace("```json", "")
                .replace("```", "")
                .trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("AI moderation response is not JSON");
        }
        return trimmed.substring(start, end + 1);
    }

    private ModerationResult localFallbackModeration(String comment) {
        String normalized = normalizeModerationText(comment);
        String compact = normalized.replace(" ", "");

        if (containsProfanity(comment, normalized, compact)) {
            return ModerationResult.flagged("PROFANITY", "HIGH",
                    "Bình luận có dấu hiệu chứa từ ngữ tục tĩu hoặc xúc phạm.", "LOCAL_RULES_FALLBACK");
        }
        if (containsSpamSignals(comment, normalized, compact)) {
            return ModerationResult.flagged("SPAM", "MEDIUM",
                    "Bình luận có dấu hiệu spam, quảng cáo hoặc lặp nội dung bất thường.", "LOCAL_RULES_FALLBACK");
        }
        return ModerationResult.approved("LOCAL_RULES_FALLBACK", "Không phát hiện dấu hiệu vi phạm bằng bộ lọc dự phòng.");
    }

    private boolean containsProfanity(String original, String normalized, String compact) {
        String lowerOriginal = original.toLowerCase(Locale.ROOT);
        for (String phrase : ACCENTED_PROFANITY) {
            if (lowerOriginal.contains(phrase)) {
                return true;
            }
        }

        for (String token : normalized.split(" ")) {
            if (PROFANE_TOKENS.contains(token)) {
                return true;
            }
        }

        for (String phrase : PROFANE_COMPACT_PHRASES) {
            if (compact.contains(phrase)) {
                return true;
            }
        }

        return false;
    }

    private boolean containsSpamSignals(String original, String normalized, String compact) {
        if (URL_PATTERN.matcher(original).find() || PHONE_PATTERN.matcher(original).find()) {
            return true;
        }
        if (REPEATED_CHAR_PATTERN.matcher(compact).find()) {
            return true;
        }
        if (hasRepeatedWords(normalized)) {
            return true;
        }
        return SPAM_PHRASES.stream().anyMatch(phrase -> containsPhrase(normalized, phrase));
    }

    private boolean hasRepeatedWords(String normalized) {
        String previous = "";
        int repeatCount = 0;
        for (String token : normalized.split(" ")) {
            if (token.length() < 2) {
                previous = token;
                repeatCount = 1;
                continue;
            }
            if (token.equals(previous)) {
                repeatCount++;
                if (repeatCount >= 4) {
                    return true;
                }
            } else {
                previous = token;
                repeatCount = 1;
            }
        }
        return false;
    }

    private boolean containsPhrase(String normalized, String phrase) {
        return (" " + normalized + " ").contains(" " + phrase + " ");
    }

    private String normalizeModerationText(String value) {
        String lower = value.toLowerCase(Locale.ROOT)
                .replace('đ', 'd');
        String noAccent = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return noAccent
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeTextValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private Object firstValue(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private boolean isUnsafeLabel(String label) {
        String normalized = normalizeViolationType(label);
        return !"NONE".equals(normalized)
                && !"NORMAL".equals(normalized)
                && !"SAFE".equals(normalized)
                && !"APPROVED".equals(normalized)
                && !"CLEAN".equals(normalized);
    }

    private String normalizeViolationType(String label) {
        if (label == null || label.isBlank()) {
            return "NONE";
        }

        String normalized = label.trim().toUpperCase(Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "_");

        return switch (normalized) {
            case "NORMAL", "SAFE", "APPROVED", "CLEAN", "NONE", "OK" -> "NONE";
            case "TOXIC", "OFFENSIVE", "PROFANE", "BAD_WORD", "BAD_WORDS" -> "PROFANITY";
            case "ADVERTISEMENT", "AD", "SCAM_LINK" -> "SPAM";
            case "ABUSE", "INSULT" -> "HARASSMENT";
            default -> normalized;
        };
    }

    private boolean readBooleanFlag(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() == 1;
        }
        String text = value == null ? "" : String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return "true".equals(text) || "yes".equals(text) || "1".equals(text) || "unsafe".equals(text);
    }

    private double readDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? 0.0 : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    public record ModerationResult(
            boolean flagged,
            String violationType,
            String severity,
            double confidence,
            String reason,
            String provider
    ) {
        static ModerationResult approved(String provider, String reason) {
            return new ModerationResult(false, "NONE", "NONE", 1.0, reason, provider);
        }

        static ModerationResult flagged(String violationType, String severity, String reason, String provider) {
            return new ModerationResult(true, violationType, severity, 1.0, reason, provider);
        }
    }
}
