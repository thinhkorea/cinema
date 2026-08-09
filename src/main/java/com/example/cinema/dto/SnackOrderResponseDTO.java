package com.example.cinema.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SnackOrderResponseDTO {
    private Long snackOrderId;
    private String orderCode;
    private String orderType;
    private String status;
    private Double totalAmount;
    private String voucherCode;
    private Double voucherDiscount;
    private String paymentMethod;
    private String note;
    private LocalDate pickupDate;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime fulfilledAt;
    private String fulfilledBy;
    private Boolean fulfilled;
    private Boolean printed;
    private LocalDateTime pickupExpiresAt;
    private Boolean pickupExpired;
    private Boolean canRedeem;
    private List<SnackOrderItemResponseDTO> items;
}
