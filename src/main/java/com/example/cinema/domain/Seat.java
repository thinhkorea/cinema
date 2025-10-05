package com.example.cinema.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "seats")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seatId;

    @Column(nullable = false)
    private String seatNumber; // Ví dụ: A1, B2, C10

    @Column(nullable = false)
    private boolean booking = false; // false = chưa đặt, true = đã đặt

    @ManyToOne
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;
}
