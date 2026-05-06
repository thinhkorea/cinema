package com.example.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SnackRecipeItemDTO {
    private Long recipeItemId;
    private Long snackId;
    private String snackName;
    private Long ingredientId;
    private String ingredientName;
    private String unit;
    private Double quantityPerSnack;
}
