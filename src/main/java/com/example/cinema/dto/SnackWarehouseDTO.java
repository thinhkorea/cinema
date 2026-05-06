package com.example.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SnackWarehouseDTO {
    private Long snackId;
    private String snackName;
    private String category;
    private Boolean warehouseTrackable;
    private Double warehouseStock;
    private Double warehouseReorderLevel;
    private Boolean lowStock;
}
