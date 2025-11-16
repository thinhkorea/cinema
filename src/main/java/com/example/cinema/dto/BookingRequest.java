package com.example.cinema.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingRequest {
    private Long showtimeId;
    private List<Long> seatIds;
    private String staffUsername;
    private Integer total;
    private String paymentMethod;
}