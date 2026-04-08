package com.example.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingSnackDTO {
    private Long id;
    private Long snackId;
    private String snackName;
    private Integer quantity;
    private Double priceAtPurchase;
    private Double subtotal;
}
