package com.example.cinema.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookings", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "seat_id", "showtime_id" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingId;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne
    @JoinColumn(name = "showtime_id", nullable = false)
    private Showtime showtime;

    @ManyToOne
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column
    private String paymentMethod;

    @Column(nullable = false)
    private String txnRef;

    @Column(nullable = false)
    private Double total = 0.0;

    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer pointsUsed = 0;

    @Column(length = 60)
    private String voucherCode;

    @Column
    private Double voucherDiscount = 0.0;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "staff_id")
    private Staff soldByStaff;

    @Column(nullable = false)
    private boolean printed = false;

    @Column(nullable = false)
    private boolean snacksFulfilled = false;

    @Column
    private Double popcornAdditionalCharge = 0.0;

    @Column(length = 30)
    private String popcornAdditionalPaymentMethod;

    @Column(length = 120)
    private String popcornAdditionalCollectedBy;

    @Column
    private LocalDateTime popcornAdditionalCollectedAt;

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = Status.PENDING;
        }
    }

    public enum Status {
        PENDING,
        PAID,
        CANCELLED
    }
}
