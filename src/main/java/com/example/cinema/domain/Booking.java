package com.example.cinema.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer pointsUsed = 0;  // Điểm tích lũy được dùng

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "staff_id")
    private Staff soldByStaff;

    @Column(nullable = false)
    private boolean printed = false;

    // Quan hệ với BookingSnacks (bắp nước đã chọn)
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingSnack> snacks = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = Status.PENDING;
        }
    }

    /**
     * Tính tổng tiền snacks
     */
    public Double getSnacksTotal() {
        return snacks.stream()
                .mapToDouble(BookingSnack::getSubtotal)
                .sum();
    }

    /**
     * Thêm snack vào booking
     */
    public void addSnack(BookingSnack bookingSnack) {
        snacks.add(bookingSnack);
        bookingSnack.setBooking(this);
    }

    /**
     * Xóa snack khỏi booking
     */
    public void removeSnack(BookingSnack bookingSnack) {
        snacks.remove(bookingSnack);
        bookingSnack.setBooking(null);
    }

    public enum Status {
        PENDING,
        PAID,
        CANCELLED
    }
}
