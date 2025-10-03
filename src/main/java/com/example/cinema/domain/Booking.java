package com.example.cinema.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.sql.Timestamp;

@Entity
@Table(name = "bookings", uniqueConstraints = @UniqueConstraint(columnNames = { "showtime_id", "seat_id" }))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // khách hàng đặt

    @ManyToOne
    @JoinColumn(name = "showtime_id", nullable = false)
    private Showtime showtime;

    @ManyToOne
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING; // mặc định chờ thanh toán

    private Timestamp createdAt = new Timestamp(System.currentTimeMillis());

    public enum Status {
        PENDING,
        PAID,
        CANCELLED
    }
}
