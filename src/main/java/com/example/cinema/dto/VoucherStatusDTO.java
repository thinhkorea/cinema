package com.example.cinema.dto;

import com.example.cinema.domain.Voucher;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherStatusDTO {
    private String code;
    private String name;
    private String description;
    private Voucher.VoucherType type;
    private Double value;
    private Double maxDiscount;
    private Double minOrder;
    private Double requiredTotalSpent;
    private Integer spendingWindowDays;
    private Double currentTotalSpent;
    private Double remainingAmount;
    private Boolean eligible;
    private Boolean claimed;
    private Boolean claimable;
    private String reason;
    private Boolean newMemberOnly;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
}
