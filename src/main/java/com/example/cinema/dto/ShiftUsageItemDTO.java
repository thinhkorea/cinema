package com.example.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftUsageItemDTO {
    private Long itemId;
    private String itemName;
    private String unit;
    private Double importedQty;
    private Double consumedQty;
    private Double adjustedQty;
    private Double closingStock;
}
