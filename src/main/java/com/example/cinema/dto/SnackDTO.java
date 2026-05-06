package com.example.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SnackDTO {
    private Long snackId;
    private String snackName;
    private String description;
    private Double price;
    private String imageUrl;
    private String category;
    private Boolean available;
    private Boolean warehouseTrackable;
    private LocalDate expiryDate;
    private String recipeInstructions;
}
