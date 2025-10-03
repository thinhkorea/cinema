package com.example.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SeatStatusDTO {
    private Long seatId;
    private String seatNumber;
    private boolean booked; // true nếu có booking
    private String status; // PENDING/PAID/CANCELLED hoặc null nếu chưa đặt
    private Long bookingId; // null nếu chưa đặt
}
