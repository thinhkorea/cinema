package com.example.cinema.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.sql.Timestamp;

@Entity
@Table(name = "tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ticketId;

    @ManyToOne
    @JoinColumn(name = "showtime_id", nullable = false)
    private Showtime showtime;

    private String seatNumber;

    @Column(nullable = false)
    private Double price;

    @ManyToOne
    @JoinColumn(name = "sold_by", nullable = false)
    private Staff soldBy;

    @Column(nullable = false, updatable = false)
    private Timestamp soldAt;

    @PrePersist
    protected void onCreate() {
        soldAt = new Timestamp(System.currentTimeMillis());
    }
}
