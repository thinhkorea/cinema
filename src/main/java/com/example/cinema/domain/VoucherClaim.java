package com.example.cinema.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "voucher_claims", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"voucher_id", "user_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoucherClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long claimId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "voucher_id")
    private Voucher voucher;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private LocalDateTime claimedAt;

    @PrePersist
    public void prePersist() {
        if (claimedAt == null) {
            claimedAt = LocalDateTime.now();
        }
    }
}
