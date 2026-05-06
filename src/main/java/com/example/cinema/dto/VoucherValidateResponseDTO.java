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
public class VoucherValidateResponseDTO {
    private boolean valid;
    private String message;
    private String code;
    private Voucher.VoucherType type;
    private Double value;
    private Double discountAmount;
    private Double finalAmount;
    private Double maxDiscount;
    private Double minOrder;
}
