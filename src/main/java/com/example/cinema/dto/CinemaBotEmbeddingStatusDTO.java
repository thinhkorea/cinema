package com.example.cinema.dto;

public record CinemaBotEmbeddingStatusDTO(
        boolean embeddingProviderAvailable,
        boolean qdrantAvailable,
        long totalMovies,
        long embeddedMovies,
        long missingMovieEmbeddings,
        long qdrantMoviePoints,
        long totalSnacks,
        long embeddedSnacks,
        long missingSnackEmbeddings,
        long qdrantSnackPoints,
        long totalPolicyDocuments,
        long embeddedPolicyDocuments,
        long missingPolicyDocumentEmbeddings,
        long qdrantPolicyDocumentPoints
) {
}
