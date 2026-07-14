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
public class VoucherResponseDTO {
    private Long voucherId;
    private String code;
    private String name;
    private String description;
    private Voucher.VoucherType type;
    private Double value;
    private Double maxDiscount;
    private Double minOrder;
    private Double requiredTotalSpent;
    private Integer spendingWindowDays;
    private Boolean active;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer usageLimit;
    private Integer usedCount;
    private Integer perUserLimit;
    private Boolean newMemberOnly;
}
