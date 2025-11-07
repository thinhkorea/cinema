package com.example.cinema.controller;

import com.example.cinema.dto.SeatStatusDTO;
import com.example.cinema.service.SeatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/seats")
public class SeatController {

    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    @GetMapping("/showtime/{showtimeId}")
    public ResponseEntity<List<SeatStatusDTO>> getSeatsForShowtime(@PathVariable Long showtimeId) {
        List<SeatStatusDTO> seats = seatService.getSeatsByShowtime(showtimeId);
        return ResponseEntity.ok(seats);
    }
}
