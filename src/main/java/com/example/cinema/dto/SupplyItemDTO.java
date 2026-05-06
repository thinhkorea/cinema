package com.example.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplyItemDTO {
    private Long supplyId;
    private String supplyName;
    private String unit;
    private Double stock;
    private Double reorderLevel;
    private Boolean active;
}
