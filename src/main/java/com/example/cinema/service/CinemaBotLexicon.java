package com.example.cinema.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.text.Normalizer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class CinemaBotLexicon {

    private static final String INTENT_PATTERNS_PATH = "chatbot/intent-patterns.yml";

    private final Map<String, List<String>> termGroups;
    private final Map<String, List<String>> movieGenreAliases;

    public CinemaBotLexicon() {
        LexiconConfig config = loadConfig();
        this.termGroups = config.termGroups();
        this.movieGenreAliases = config.genreAliases();
    }

    public String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(normalized).replaceAll("")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D')
                .trim();
    }

    public boolean contains(String normalizedText, String phrase) {
        return normalizedText != null && normalizedText.contains(normalize(phrase));
    }

    public boolean equals(String normalizedText, String phrase) {
        return normalizedText != null && normalizedText.equals(normalize(phrase));
    }

    public boolean equalsAny(String normalizedText, String... phrases) {
        if (normalizedText == null) return false;
        for (String phrase : phrases) {
            if (normalizedText.equals(normalize(phrase))) {
                return true;
            }
        }
        return false;
    }

    public boolean containsAny(String normalizedText, String... phrases) {
        for (String phrase : phrases) {
            if (contains(normalizedText, phrase)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsAnyGroup(String normalizedText, String groupName) {
        for (String phrase : termGroups.getOrDefault(groupName, Collections.emptyList())) {
            if (contains(normalizedText, phrase)) {
                return true;
            }
        }
        return false;
    }

    public boolean equalsAnyGroup(String normalizedText, String groupName) {
        for (String phrase : termGroups.getOrDefault(groupName, Collections.emptyList())) {
            if (equals(normalizedText, phrase)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsWord(String normalizedText, String phrase) {
        if (normalizedText == null) return false;
        String normalizedPhrase = Pattern.quote(normalize(phrase));
        return Pattern.compile("(^|\\s)" + normalizedPhrase + "(\\s|$)")
                .matcher(normalizedText)
                .find();
    }

    public String resolveMovieGenre(String normalizedText) {
        for (Map.Entry<String, List<String>> entry : movieGenreAliases.entrySet()) {
            for (String alias : entry.getValue()) {
                if (contains(normalizedText, alias)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private LexiconConfig loadConfig() {
        try (InputStream inputStream = new ClassPathResource(INTENT_PATTERNS_PATH).getInputStream()) {
            Map<String, Object> root = new Yaml().load(inputStream);
            return new LexiconConfig(
                    readStringListMap((Map<String, Object>) root.get("termGroups")),
                    readStringListMap((Map<String, Object>) root.get("genreAliases"))
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể tải cấu hình chatbot từ " + INTENT_PATTERNS_PATH, ex);
        }
    }

    private Map<String, List<String>> readStringListMap(Map<String, Object> source) {
        if (source == null) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getValue() instanceof List<?> values) {
                result.put(entry.getKey(), values.stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .collect(Collectors.toList()));
            }
        }
        return result;
    }

    private record LexiconConfig(
            Map<String, List<String>> termGroups,
            Map<String, List<String>> genreAliases
    ) {
    }
}
