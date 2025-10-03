package com.example.cinema.controller;

import com.example.cinema.domain.Showtime;
import com.example.cinema.service.ShowtimeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/showtimes")
public class ShowtimeController {
    private final ShowtimeService showtimeService;

    public ShowtimeController(ShowtimeService showtimeService) {
        this.showtimeService = showtimeService;
    }

    // GET all showtimes
    @GetMapping
    public ResponseEntity<List<Showtime>> getAllShowtimes() {
        return ResponseEntity.ok(showtimeService.findAll());
    }

    // GET showtime by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getShowtimeById(@PathVariable Long id) {
        return showtimeService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // GET showtimes by movie ID
    @GetMapping("/movie/{movieId}")
    public ResponseEntity<List<Showtime>> getShowtimesByMovie(@PathVariable Long movieId) {
        return ResponseEntity.ok(showtimeService.findByMovieId(movieId));
    }

    // CREATE new showtime
    @PostMapping
    public ResponseEntity<Showtime> createShowtime(@RequestBody Showtime showtime) {
        return ResponseEntity.ok(showtimeService.save(showtime));
    }

    // UPDATE existing showtime
    @PutMapping("/{id}")
    public ResponseEntity<?> updateShowtime(@PathVariable Long id, @RequestBody Showtime updatedShowtime) {
        return showtimeService.findById(id)
                .map(existing -> {
                    existing.setMovie(updatedShowtime.getMovie());
                    existing.setRoom(updatedShowtime.getRoom());
                    existing.setStartTime(updatedShowtime.getStartTime());
                    return ResponseEntity.ok(showtimeService.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // DELETE showtime
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteShowtime(@PathVariable Long id) {
        if (showtimeService.findById(id).isPresent()) {
            showtimeService.delete(id);
            return ResponseEntity.ok("Showtime deleted");
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
