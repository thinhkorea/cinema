package com.example.cinema.service;

import java.util.Locale;

public enum CinemaBotIntent {
    GENERAL,
    MOVIES,
    MOVIE_DETAIL,
    SHOWTIMES,
    SNACKS,
    LOYALTY,
    VOUCHERS,
    BOOKING_INFO,
    SECURITY_REQUEST;

    public static CinemaBotIntent from(String value) {
        if (value == null || value.isBlank()) {
            return GENERAL;
        }

        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        return switch (normalized) {
            case "MOVIE_SEARCH" -> MOVIES;
            case "SHOWTIME_SEARCH", "SHOWTIME" -> SHOWTIMES;
            case "BOOKING_GUIDANCE", "TICKET_LOOKUP", "PAYMENT_STATUS" -> BOOKING_INFO;
            case "SNACK_POLICY" -> SNACKS;
            case "VOUCHER_POLICY" -> VOUCHERS;
            default -> parseKnownIntent(normalized);
        };
    }

    public boolean isContextAware() {
        return switch (this) {
            case MOVIES, MOVIE_DETAIL, SHOWTIMES, SNACKS, LOYALTY, VOUCHERS, BOOKING_INFO -> true;
            default -> false;
        };
    }

    private static CinemaBotIntent parseKnownIntent(String normalized) {
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return GENERAL;
        }
    }
}
