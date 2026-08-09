package com.example.cinema.service;

import com.example.cinema.domain.Movie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.text.Normalizer;
import java.util.ArrayList;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class MovieDiscoveryRerankService {

    private static final Logger log = LoggerFactory.getLogger(MovieDiscoveryRerankService.class);

    private static final int MAX_RERANK_DOCUMENT_LENGTH = 1200;
    private static final int MIN_DESCRIPTION_SNIPPET_BUDGET = 450;
    private static final Pattern NON_WORD_PATTERN = Pattern.compile("[^a-z0-9]+");
    private static final Pattern CHARACTER_NAME_PATTERN = Pattern.compile(
            "(?U)\\b\\p{Lu}[\\p{L}\\p{M}\\p{Nd}'’.-]*\\b(?:\\s+\\b\\p{Lu}[\\p{L}\\p{M}\\p{Nd}'’.-]*\\b){0,3}"
    );
    private static final Set<String> RERANK_STOP_WORDS = Set.of(
            "toi", "minh", "em", "anh", "chi", "ban", "nguoi", "phim", "tim", "xem", "nho", "ten",
            "mot", "co", "la", "va", "thi", "ma", "roi", "sau", "truoc", "khi", "luc", "o", "ve",
            "cua", "cho", "voi", "duoc", "bi", "ra", "vao", "do", "ay", "nay", "kia"
    );
    private static final Set<String> CHARACTER_NAME_STOP_WORDS = Set.of(
            "anh", "ba", "ban", "cau", "chuyen", "con", "co", "cuoc", "day", "dieu",
            "do", "dut", "gia", "hai", "hom", "khi", "loi", "luc", "mot", "nam", "nay", "ngay",
            "nguoi", "nhan", "nhung", "nua", "phim", "sau", "song", "tai", "the", "trong",
            "truoc", "tu", "tuy", "vao", "vi", "voi"
    );
    private static final List<String> MALE_RELATION_TERMS = List.of(
            "ong", "bo", "ba", "cha", "con trai", "anh", "chu", "cau", "chang",
            "nam", "dan ong", "ong noi", "nguoi cha", "nguoi bo"
    );
    private static final List<String> FEMALE_RELATION_TERMS = List.of(
            "ba", "me", "ma", "con gai", "co", "chi", "nang", "nu", "phu nu",
            "ba ngoai", "ba noi", "con dau", "vo", "tieu thu", "cong chua"
    );

    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final String rerankUrl;
    private final String rerankModelName;

    public MovieDiscoveryRerankService(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${cinema.movie-discovery.rerank-enabled:true}") boolean enabled,
            @Value("${cinema.movie-discovery.rerank-url:http://localhost:8002/rerank-movies}") String rerankUrl,
            @Value("${cinema.movie-discovery.rerank-model:BAAI/bge-reranker-v2-m3}") String rerankModelName,
            @Value("${cinema.movie-discovery.rerank-connect-timeout-seconds:3}") int connectTimeoutSeconds,
            @Value("${cinema.movie-discovery.rerank-read-timeout-seconds:5}") int readTimeoutSeconds
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
                    .map(candidate -> toPayload(candidate, query))
                    .collect(Collectors.toList()));

            Map<?, ?> response = restTemplate.postForObject(
                    rerankUrl,
                    new HttpEntity<>(request, jsonHeaders()),
                    Map.class
            );
            return parseResponse(response);
        } catch (RestClientException ex) {
            log.warn(
                    "[MovieDiscovery] Rerank request failed: url={}, model={}, candidates={}, cause={}",
                    rerankUrl,
                    rerankModelName,
                    candidates.size(),
                    ex.getMessage()
            );
            return RerankResponse.empty();
        }
    }

    private Map<String, Object> toPayload(RerankCandidate candidate, String query) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("movieId", candidate.movieId());
        payload.put("title", candidate.title());
        payload.put("genre", candidate.genre());
        payload.put("description", candidate.description());
        payload.put("actors", candidate.actors());
        payload.put("score", candidate.score());
        payload.put("semanticScore", candidate.semanticScore());
        payload.put("document", buildRerankDocument(candidate, query));
        return payload;
    }

    private String buildRerankDocument(RerankCandidate candidate, String query) {
        String header = joinNonBlank(List.of(
                safe(candidate.title()),
                safe(candidate.genre()),
                safe(candidate.actors()),
                buildRerankMetadata(candidate)
        ));
        String description = safe(candidate.description()).trim();
        if (description.isBlank()) {
            return truncate(header, MAX_RERANK_DOCUMENT_LENGTH);
        }

        String fullDocument = String.join("\n", header, description).trim();
        if (fullDocument.length() <= MAX_RERANK_DOCUMENT_LENGTH) {
            return fullDocument;
        }

        int descriptionBudget = Math.max(
                MIN_DESCRIPTION_SNIPPET_BUDGET,
                MAX_RERANK_DOCUMENT_LENGTH - header.length() - 2
        );
        String selectedDescription = selectRelevantDescription(description, query, descriptionBudget);
        return truncate(String.join("\n", header, selectedDescription).trim(), MAX_RERANK_DOCUMENT_LENGTH);
    }

    private String buildRerankMetadata(RerankCandidate candidate) {
        String description = safe(candidate.description());
        List<String> actors = splitPeople(candidate.actors());
        List<String> mentionedActors = actorsMentionedInDescription(actors, description);
        List<String> characterNames = extractCharacterNames(description, candidate.title(), 17);
        List<String> relationSignals = genderRelationSignals(description, 10);

        List<String> lines = new ArrayList<>();
        if (!actors.isEmpty()) {
            lines.add("Dien vien chinh: " + String.join(", ", actors.subList(0, Math.min(actors.size(), 8))));
            lines.add("So dien vien chinh duoc khai bao: " + actors.size());
            lines.add("So dien vien chinh xuat hien trong mo ta: " + mentionedActors.size());
        }
        if (!characterNames.isEmpty()) {
            lines.add("Ten nhan vat/ten rieng trong mo ta: " + String.join(", ", characterNames));
            lines.add("So ten nhan vat/tin hieu rieng trong mo ta: " + characterNames.size());
        }
        if (!relationSignals.isEmpty()) {
            lines.add("Tin hieu gioi tinh/quan he: " + String.join("; ", relationSignals));
        }
        return joinNonBlank(lines);
    }

    private List<String> splitPeople(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> people = new LinkedHashSet<>();
        for (String item : value.split("[,;/|]")) {
            String cleaned = item.replaceAll("\\([^)]*\\)", " ").replaceAll("\\s+", " ").trim();
            if (!cleaned.isBlank()) {
                people.add(cleaned);
            }
        }
        return List.copyOf(people);
    }

    private List<String> actorsMentionedInDescription(List<String> actors, String description) {
        if (actors.isEmpty() || description == null || description.isBlank()) {
            return Collections.emptyList();
        }
        String normalizedDescription = normalize(description);
        List<String> mentioned = new ArrayList<>();
        for (String actor : actors) {
            String normalizedActor = normalize(actor);
            if (!normalizedActor.isBlank() && containsWholePhrase(normalizedDescription, normalizedActor)) {
                mentioned.add(actor);
            }
        }
        return mentioned;
    }

    private List<String> extractCharacterNames(String description, String title, int maxNames) {
        if (description == null || description.isBlank()) {
            return Collections.emptyList();
        }
        Set<String> titleAliases = normalizedTitleAliases(title);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        Matcher matcher = CHARACTER_NAME_PATTERN.matcher(description);
        while (matcher.find() && names.size() < maxNames) {
            String candidate = matcher.group().replaceAll("\\s+", " ").replaceAll("^[\\s.,;:()\\[\\]]+|[\\s.,;:()\\[\\]]+$", "");
            if (candidate.isBlank()) {
                continue;
            }
            String normalizedCandidate = normalize(candidate);
            if (isExcludedCharacterName(candidate, normalizedCandidate, titleAliases)) {
                continue;
            }
            names.add(candidate);
        }
        return List.copyOf(names);
    }

    private Set<String> normalizedTitleAliases(String title) {
        if (title == null || title.isBlank()) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        aliases.add(normalize(title));
        aliases.add(normalize(title.replaceAll("\\([^)]*\\)", " ")));
        Matcher matcher = Pattern.compile("\\(([^)]+)\\)").matcher(title);
        while (matcher.find()) {
            aliases.add(normalize(matcher.group(1)));
        }
        aliases.remove("");
        return aliases;
    }

    private boolean isExcludedCharacterName(String candidate, String normalizedCandidate, Set<String> titleAliases) {
        if (normalizedCandidate.isBlank() || titleAliases.contains(normalizedCandidate)) {
            return true;
        }
        boolean singleInitial = candidate.length() == 1 && Character.isUpperCase(candidate.charAt(0));
        if (normalizedCandidate.length() < 2 && !singleInitial) {
            return true;
        }
        List<String> words = List.of(normalizedCandidate.split("\\s+"));
        return !words.isEmpty() && words.stream().allMatch(CHARACTER_NAME_STOP_WORDS::contains);
    }

    private List<String> genderRelationSignals(String description, int maxTerms) {
        String normalizedDescription = normalize(description);
        List<String> maleTerms = matchedTerms(normalizedDescription, MALE_RELATION_TERMS, maxTerms);
        List<String> femaleTerms = matchedTerms(normalizedDescription, FEMALE_RELATION_TERMS, maxTerms);

        List<String> signals = new ArrayList<>();
        if (!maleTerms.isEmpty()) {
            signals.add("nam/quan he nam: " + String.join(", ", maleTerms));
        }
        if (!femaleTerms.isEmpty()) {
            signals.add("nu/quan he nu: " + String.join(", ", femaleTerms));
        }
        return signals;
    }

    private List<String> matchedTerms(String normalizedText, List<String> terms, int maxTerms) {
        List<String> result = new ArrayList<>();
        for (String term : terms) {
            if (containsWholePhrase(normalizedText, term)) {
                result.add(term);
                if (result.size() >= maxTerms) {
                    break;
                }
            }
        }
        return result;
    }

    private String joinNonBlank(List<String> values) {
        return values.stream()
                .map(this::safe)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private String selectRelevantDescription(String description, String query, int budget) {
        List<String> queryTerms = buildQueryTerms(query);
        String[] rawChunks = description.split("(?<=[.!?])\\s+|\\R+");
        List<DescriptionChunk> chunks = new ArrayList<>();
        for (int index = 0; index < rawChunks.length; index++) {
            String chunk = rawChunks[index] == null ? "" : rawChunks[index].trim();
            if (chunk.isBlank()) continue;
            chunks.add(new DescriptionChunk(index, chunk, scoreDescriptionChunk(chunk, queryTerms)));
        }

        if (chunks.isEmpty()) {
            return truncate(description, budget);
        }

        List<DescriptionChunk> selected = chunks.stream()
                .sorted(Comparator
                        .comparingDouble(DescriptionChunk::score)
                        .reversed()
                        .thenComparingInt(DescriptionChunk::index))
                .limit(4)
                .filter(chunk -> chunk.score() > 0.0)
                .collect(Collectors.toCollection(ArrayList::new));

        if (selected.isEmpty()) {
            return truncate(chunks.get(0).text(), budget);
        }

        selected.sort(Comparator.comparingInt(DescriptionChunk::index));
        StringBuilder builder = new StringBuilder();
        for (DescriptionChunk chunk : selected) {
            String text = chunk.text();
            if (builder.length() > 0) {
                if (builder.length() + 1 + text.length() > budget) break;
                builder.append('\n');
            }
            if (builder.length() + text.length() > budget) {
                int remaining = budget - builder.length();
                if (remaining > 80) {
                    builder.append(truncate(text, remaining));
                }
                break;
            }
            builder.append(text);
        }

        if (builder.length() == 0) {
            return truncate(selected.get(0).text(), budget);
        }
        return builder.toString();
    }

    private double scoreDescriptionChunk(String chunk, List<String> queryTerms) {
        if (queryTerms.isEmpty()) {
            return 0.0;
        }
        String normalizedChunk = normalize(chunk);
        double score = 0.0;
        for (String term : queryTerms) {
            if (normalizedChunk.contains(term)) {
                score += term.contains(" ") ? 2.5 : 1.0;
            }
        }
        return score;
    }

    private List<String> buildQueryTerms(String query) {
        String normalizedQuery = normalize(query);
        List<String> words = List.of(normalizedQuery.split("\\s+")).stream()
                .filter(word -> word.length() >= 3)
                .filter(word -> !RERANK_STOP_WORDS.contains(word))
                .distinct()
                .collect(Collectors.toList());
        LinkedHashSet<String> terms = new LinkedHashSet<>(words);
        for (int size = 3; size >= 2; size--) {
            for (int index = 0; index <= words.size() - size; index++) {
                terms.add(String.join(" ", words.subList(index, index + size)));
            }
        }
        return List.copyOf(terms);
    }

    private boolean containsWholePhrase(String text, String phrase) {
        if (text == null || phrase == null || text.isBlank() || phrase.isBlank()) {
            return false;
        }
        return (" " + text + " ").contains(" " + phrase + " ");
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String decomposed = Normalizer.normalize(value.toLowerCase(), Normalizer.Form.NFD);
        String withoutMarks = decomposed.replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'd');
        return NON_WORD_PATTERN.matcher(withoutMarks).replaceAll(" ").trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, maxLength));
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

    private record DescriptionChunk(int index, String text, double score) {
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
