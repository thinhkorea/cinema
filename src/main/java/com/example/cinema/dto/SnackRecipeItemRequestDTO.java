package com.example.cinema.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SnackRecipeItemRequestDTO {
    private Long ingredientId;
    private Double quantityPerSnack;
}
