package com.example.cinema.dto;

import lombok.Data;

@Data
public class MovieReviewRequest {
    private Integer rating;
    private String comment;
}
