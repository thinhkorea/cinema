package com.example.cinema.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SnackOrderResponseDTO {
    private Long snackOrderId;
    private String orderCode;
    private String status;
    private Double totalAmount;
    private String paymentMethod;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private List<SnackOrderItemResponseDTO> items;
}
