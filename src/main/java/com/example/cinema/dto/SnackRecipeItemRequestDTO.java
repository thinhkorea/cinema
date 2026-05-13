package com.example.cinema.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SnackRecipeItemRequestDTO {
    private Long ingredientId;
    private Double quantityPerSnack;
}
