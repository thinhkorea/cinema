package com.example.cinema.dto;

public record CinemaBotEmbeddingStatusDTO(
        boolean embeddingProviderAvailable,
        long totalMovies,
        long embeddedMovies,
        long missingMovieEmbeddings,
        long totalSnacks,
        long embeddedSnacks,
        long missingSnackEmbeddings
) {
}
