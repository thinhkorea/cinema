package com.example.cinema.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RedeemPointsRequestDTO {
    private String txnRef;
    private Integer pointsToUse;
}
