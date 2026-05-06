package com.example.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftSummaryDTO {
    private LocalDate date;
    private List<ShiftUsageItemDTO> ingredients;
    private List<ShiftUsageItemDTO> supplies;
    private List<ShiftUsageItemDTO> snackWarehouse;
}
