package com.example.cinema.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "vouchers", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "code" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long voucherId;

    @Column(nullable = false, length = 60, unique = true)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VoucherType type = VoucherType.PERCENT;

    @Column(nullable = false)
    private Double value = 0.0;

    @Column
    private Double maxDiscount;

    @Column
    private Double minOrder;

    @Column
    private Double requiredTotalSpent;

    @Column
    private Integer spendingWindowDays;

    @Column(nullable = false)
    private Boolean active = true;

    @Column
    private LocalDateTime startAt;

    @Column
    private LocalDateTime endAt;

    @Column
    private Integer usageLimit;

    @Column(nullable = false)
    private Integer usedCount = 0;

    @Column
    private Integer perUserLimit = 1;

    @Column(nullable = false)
    private Boolean newMemberOnly = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (active == null) {
            active = true;
        }
        if (usedCount == null) {
            usedCount = 0;
        }
        if (perUserLimit == null) {
            perUserLimit = 1;
        }
        if (newMemberOnly == null) {
            newMemberOnly = false;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum VoucherType {
        PERCENT,
        FIXED
    }
}
