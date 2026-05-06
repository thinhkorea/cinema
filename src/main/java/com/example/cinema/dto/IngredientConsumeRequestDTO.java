package com.example.cinema.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IngredientConsumeRequestDTO {
    private Double quantity;
    private String reason;
    private String note;
}
