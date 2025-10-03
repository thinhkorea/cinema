package com.example.cinema.controller;

import com.example.cinema.domain.Seat;
import com.example.cinema.service.SeatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seats")
public class SeatController {
    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    @GetMapping
    public ResponseEntity<List<Seat>> getAllSeats() {
        return ResponseEntity.ok(seatService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getSeatById(@PathVariable Long id) {
        return seatService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/room/{roomId}")
    public ResponseEntity<List<Seat>> getSeatsByRoom(@PathVariable Long roomId) {
        return ResponseEntity.ok(seatService.findByRoom(roomId));
    }

    @PostMapping
    public ResponseEntity<Seat> createSeat(@RequestBody Seat seat) {
        return ResponseEntity.ok(seatService.save(seat));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateSeat(@PathVariable Long id, @RequestBody Seat updatedSeat) {
        return seatService.findById(id).map(seat -> {
            seat.setSeatNumber(updatedSeat.getSeatNumber());
            seat.setRoom(updatedSeat.getRoom());
            return ResponseEntity.ok(seatService.save(seat));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSeat(@PathVariable Long id) {
        if (seatService.findById(id).isPresent()) {
            seatService.delete(id);
            return ResponseEntity.ok("Seat deleted");
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
