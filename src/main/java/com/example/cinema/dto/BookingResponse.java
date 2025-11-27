package com.example.cinema.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingResponse {
    private Long bookingId;
    private String username;
    private String soldByStaff;
    private String movieTitle;
    private String roomName;
    private String seatNumber;
    private String showtime;
    private String status;
    private LocalDateTime createdAt;
}
