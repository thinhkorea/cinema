package com.example.cinema.dto;

import java.time.LocalDateTime;

public record CinemaBotShowtimeSuggestionDTO(
        Long showtimeId,
        Long movieId,
        String movieTitle,
        String posterUrl,
        String roomName,
        String roomType,
        LocalDateTime startTime,
        Double price,
        String bookingPath
) {
}
