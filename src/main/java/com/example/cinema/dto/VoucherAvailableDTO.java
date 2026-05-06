package com.example.cinema.dto;

import com.example.cinema.domain.Voucher;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherAvailableDTO {
    private String code;
    private String name;
    private String description;
    private Voucher.VoucherType type;
    private Double value;
    private Double maxDiscount;
    private Double minOrder;
    private Double discountAmount;
    private Double finalAmount;
    private Boolean newMemberOnly;
}
