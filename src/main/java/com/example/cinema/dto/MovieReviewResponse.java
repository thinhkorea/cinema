package com.example.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovieReviewResponse {
    private Long reviewId;
    private Integer rating;
    private String comment;
    private String username;
    private String fullName;
    private LocalDateTime createdAt;
}
