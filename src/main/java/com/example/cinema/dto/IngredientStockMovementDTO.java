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
public class IngredientStockMovementDTO {
    private Long movementId;
    private Long ingredientId;
    private String ingredientName;
    private Double quantityBefore;
    private Double quantityChange;
    private Double quantityAfter;
    private String action;
    private String actionDisplay;
    private String reason;
    private String note;
    private String performedBy;
    private LocalDateTime createdAt;
}
