package com.example.cinema.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BookingRequest {
    @NotNull(message = "showtimeId is required")
    private Long showtimeId;

    @NotNull(message = "seatId is required")
    private Long seatId;
}
