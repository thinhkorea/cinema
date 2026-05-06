package com.example.cinema.dto;

import com.example.cinema.domain.PointTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointTransactionDTO {

    private Long id;
    private String type;
    private String typeLabel;
    private Integer points;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime expiredAt;
    private boolean expired;
    private Long bookingId;

    public static PointTransactionDTO from(PointTransaction pt) {
        return PointTransactionDTO.builder()
                .id(pt.getId())
                .type(pt.getType().name())
                .typeLabel(getTypeLabel(pt.getType()))
                .points(pt.getPoints())
                .description(pt.getDescription())
                .createdAt(pt.getCreatedAt())
                .expiredAt(pt.getExpiredAt())
                .expired(pt.isExpired())
                .bookingId(pt.getBooking() != null ? pt.getBooking().getBookingId() : null)
                .build();
    }

    private static String getTypeLabel(PointTransaction.Type type) {
        switch (type) {
            case EARNED: return "Tích điểm mua vé";
            case REFUND_CANCEL: return "Hoàn điểm hủy vé";
            case USED: return "Dùng điểm giảm giá";
            default: return type.name();
        }
    }
}
