package com.example.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovieReviewResponseDTO {
    private Long reviewId;
    private Long movieId;
    private String movieTitle;
    private Long userId;
    private Integer rating;
    private String comment;
    private String username;
    private String fullName;
    private String moderationStatus;
    private Boolean flagged;
    private String violationType;
    private String violationSeverity;
    private String violationReason;
    private LocalDateTime createdAt;
}
