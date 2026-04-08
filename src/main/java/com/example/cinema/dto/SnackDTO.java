package com.example.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SnackDTO {
    private Long snackId;
    private String snackName;
    private String description;
    private Double price;
    private String imageUrl;
    private Integer stock;
    private String category;
    private Boolean available;
}
