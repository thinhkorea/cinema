package com.example.cinema.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class CinemaBotLexicon {

    private static final Map<String, List<String>> MOVIE_GENRE_ALIASES = Map.of(
            "tình cảm", List.of("tình cảm", "tình", "lãng mạn", "tình yêu", "romance"),
            "hành động", List.of("hành động", "võ thuật", "chiến đấu", "đánh nhau", "action"),
            "kinh dị", List.of("kinh dị", "ma", "rùng rợn", "horror"),
            "hoạt hình", List.of("hoạt hình", "animation", "anime"),
            "hài", List.of("hài", "hài hước", "vui nhộn", "comedy"),
            "phiêu lưu", List.of("phiêu lưu", "mạo hiểm", "adventure"),
            "khoa học viễn tưởng", List.of("khoa học viễn tưởng", "viễn tưởng", "sci-fi", "science fiction"),
            "tâm lý", List.of("tâm lý", "chính kịch", "drama")
    );

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

    public boolean containsWord(String normalizedText, String phrase) {
        if (normalizedText == null) return false;
        String normalizedPhrase = Pattern.quote(normalize(phrase));
        return Pattern.compile("(^|\\s)" + normalizedPhrase + "(\\s|$)")
                .matcher(normalizedText)
                .find();
    }

    public String resolveMovieGenre(String normalizedText) {
        for (Map.Entry<String, List<String>> entry : MOVIE_GENRE_ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                if (contains(normalizedText, alias)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }
}
