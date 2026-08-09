package com.example.cinema.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateSnackOrderRequestDTO {
    private List<SnackItemRequestDTO> items;
    private String note;
    private LocalDate pickupDate;
}
