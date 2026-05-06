package com.example.cinema.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class MovieReviewRequestDTO implements Serializable {
    private Integer rating;
    private String comment;
}
