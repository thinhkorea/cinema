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
    private Long userId;
    private Integer rating;
    private String comment;
    private String username;
    private String fullName;
    private LocalDateTime createdAt;
}
