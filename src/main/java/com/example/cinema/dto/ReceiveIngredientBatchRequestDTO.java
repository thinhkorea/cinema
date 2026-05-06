package com.example.cinema.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class ReceiveIngredientBatchRequestDTO {
    private Double quantity;
    private Double unitCost;
    private String supplier;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private String note;
}
