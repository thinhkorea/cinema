package com.example.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftRevenueItemDTO {
    private String shiftKey;
    private String shiftLabel;
    private String timeRange;
    private String staffName;
    private Double ticketCashRevenue;
    private Double ticketVnpayRevenue;
    private Double popcornCashRevenue;
    private Double popcornVnpayRevenue;
    private Double popcornBankRevenue;
    private Double totalRevenue;
}
