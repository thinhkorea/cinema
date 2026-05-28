package com.example.cinema.controller;

import com.example.cinema.domain.Seat;
import com.example.cinema.dto.SeatStatusDTO;
import com.example.cinema.service.SeatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/room/{roomId}")
    public ResponseEntity<List<Seat>> getSeatsForRoom(@PathVariable Long roomId) {
        return ResponseEntity.ok(seatService.findByRoom(roomId));
    }

    @PostMapping("/room/{roomId}")
    public ResponseEntity<Seat> createSeatForRoom(@PathVariable Long roomId, @RequestBody Seat request) {
        return ResponseEntity.ok(seatService.createSeatForRoom(roomId, request));
    }

    @PutMapping("/{seatId}")
    public ResponseEntity<Seat> updateSeat(@PathVariable Long seatId, @RequestBody Seat request) {
        return ResponseEntity.ok(seatService.updateSeat(seatId, request));
    }

    @DeleteMapping("/{seatId}")
    public ResponseEntity<?> deleteSeat(@PathVariable Long seatId) {
        seatService.deleteSeat(seatId);
        return ResponseEntity.ok().build();
    }
}
