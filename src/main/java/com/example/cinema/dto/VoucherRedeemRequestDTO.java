package com.example.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoucherRedeemRequestDTO {
    private String txnRef;
    private String code;
    private Double totalAmount;
}
