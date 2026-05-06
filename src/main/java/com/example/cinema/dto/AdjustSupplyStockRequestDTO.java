package com.example.cinema.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdjustSupplyStockRequestDTO {
    private Double quantity;
    private String operation; // SET, ADD, SUBTRACT
    private String note;
}
