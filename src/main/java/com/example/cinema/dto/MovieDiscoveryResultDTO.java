package com.example.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovieDiscoveryResultDTO {
    private Long movieId;
    private String title;
    private Integer duration;
    private String genre;
    private String description;
    private String posterUrl;
    private String trailerUrl;
    private String status;
    private String ageRating;
    private String actors;
    private double score;
    private Double semanticScore;
    private Double rerankScore;
    private long processingTimeMs;
    private long semanticSearchTimeMs;
    private long rerankTimeMs;
    private String matchReason;
    private String matchSource;
    private String modelName;
    private List<String> matchedSignals;
}
