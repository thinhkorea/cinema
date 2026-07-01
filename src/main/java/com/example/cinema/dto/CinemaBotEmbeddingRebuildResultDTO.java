package com.example.cinema.dto;

public record CinemaBotEmbeddingRebuildResultDTO(
        int processedMovies,
        int updatedMovies,
        int failedMovies,
        int processedSnacks,
        int updatedSnacks,
        int failedSnacks,
        String message
) {
}
