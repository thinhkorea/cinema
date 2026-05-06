package com.example.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngredientBatchDTO {
    private Long batchId;
    private Long ingredientId;
    private String ingredientName;
    private Double quantityReceived;
    private Double quantityRemaining;
    private Double unitCost;
    private String supplier;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private LocalDateTime receivedAt;
    private String note;
}
