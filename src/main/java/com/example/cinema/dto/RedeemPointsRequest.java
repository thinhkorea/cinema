package com.example.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedeemPointsRequest {
    private String txnRef;
    private Integer pointsToUse;  // Số điểm khách muốn dùng
}
