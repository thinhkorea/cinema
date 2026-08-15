package com.example.cinema.service;

import java.util.List;
import java.util.stream.Collectors;

public class MovieDiscoveryRerankDocumentFormatter {

    public static final String LEGACY = "legacy";
    public static final String V2_CLEAN = "v2_clean";

    public boolean isV2Clean(String documentFormat) {
        return V2_CLEAN.equals(normalizeFormat(documentFormat));
    }

    public String formatV2Clean(MovieDiscoveryRerankService.RerankCandidate candidate) {
        return String.join("\n", List.of(
                "Title: " + safe(candidate.title()),
                "Genre: " + safe(candidate.genre()),
                "Cast: " + safe(candidate.actors()),
                "Plot: " + safe(candidate.description())
        ));
    }

    public String normalizeFormat(String documentFormat) {
        if (documentFormat == null || documentFormat.isBlank()) {
            return LEGACY;
        }
        String normalized = documentFormat.trim().toLowerCase();
        if (V2_CLEAN.equals(normalized)) {
            return V2_CLEAN;
        }
        return LEGACY;
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .collect(Collectors.joining(" "));
    }
}
