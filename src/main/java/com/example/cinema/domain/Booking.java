package com.example.cinema.domain;

import jakarta.persistence.*;
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
@EntityListeners(AuditingEntityListener.class) // 1. Bật "listener" của Auditing cho class này
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

    @ManyToOne // Một booking chỉ ứng với 1 ghế trong 1 suất chiếu
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

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "staff_id")
    private Staff soldByStaff;

    @Column(nullable = false)
    private boolean printed = false;

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
