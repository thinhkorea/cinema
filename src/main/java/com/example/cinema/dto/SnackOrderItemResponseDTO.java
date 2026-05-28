package com.example.cinema.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SnackOrderItemResponseDTO {
    private Long snackId;
    private String snackName;
    private String imageUrl;
    private Integer quantity;
    private Double priceAtPurchase;
    private Double subtotal;
}
