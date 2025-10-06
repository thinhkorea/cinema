package com.example.cinema.dto;

import java.sql.Timestamp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingResponse {
    private Long bookingId;
    private String username;
    private String movieTitle;
    private String roomName;
    private String seatNumber;
    private String showtime;
    private String status;
    private Timestamp createdAt;
}
