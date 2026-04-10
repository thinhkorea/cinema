package com.example.cinema.controller;

import com.example.cinema.domain.Movie;
import com.example.cinema.domain.Room;
import com.example.cinema.domain.Showtime;
import com.example.cinema.dto.ShowtimeRequest;
import com.example.cinema.repository.MovieRepository;
import com.example.cinema.repository.RoomRepository;
import com.example.cinema.repository.ShowtimeRepository;
import com.example.cinema.service.SeatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.DayOfWeek;

@RestController
@RequestMapping("/api/showtimes")
public class ShowtimeController {

    private static final double WEEKDAY_PRICE = 65000.0;
    private static final double WEEKEND_PRICE = 80000.0;

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final RoomRepository roomRepository;
    private final SeatService seatService;

    public ShowtimeController(ShowtimeRepository showtimeRepository,
            MovieRepository movieRepository,
            RoomRepository roomRepository,
            SeatService seatService) {
        this.showtimeRepository = showtimeRepository;
        this.movieRepository = movieRepository;
        this.roomRepository = roomRepository;
        this.seatService = seatService;
    }

    // GET all showtimes
    @GetMapping
    public ResponseEntity<List<Showtime>> getAllShowtimes() {
        return ResponseEntity.ok(showtimeRepository.findAll());
    }

    // GET showtime by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getShowtimeById(@PathVariable Long id) {
        return showtimeRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // GET showtimes by movie ID
    @GetMapping("/movie/{movieId}")
    public ResponseEntity<List<Showtime>> getShowtimesByMovie(@PathVariable Long movieId) {
        List<Showtime> showtimes = showtimeRepository.findByMovie_MovieId(movieId);
        return ResponseEntity.ok(showtimes);
    }

    // CREATE new showtime (fix lỗi Data integrity violation)
    @PostMapping
    public ResponseEntity<?> createShowtime(@RequestBody ShowtimeRequest req) {
        Movie movie = movieRepository.findById(req.getMovieId())
                .orElseThrow(() -> new IllegalArgumentException("Movie not found"));
        Room room = roomRepository.findById(req.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        // Initialize seats for room if not already done
        seatService.initializeSeatsForRoom(room);

        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setRoom(room);
        showtime.setStartTime(req.getStartTime());
        showtime.setEndTime(req.getEndTime());
        showtime.setPrice(resolvePrice(req));

        Showtime saved = showtimeRepository.save(showtime);
        return ResponseEntity.ok(saved);
    }

    // UPDATE existing showtime
    @PutMapping("/{id}")
    public ResponseEntity<?> updateShowtime(@PathVariable Long id, @RequestBody ShowtimeRequest req) {
        return showtimeRepository.findById(id)
                .map(existing -> {
                    Movie movie = movieRepository.findById(req.getMovieId())
                            .orElseThrow(() -> new IllegalArgumentException("Movie not found"));
                    Room room = roomRepository.findById(req.getRoomId())
                            .orElseThrow(() -> new IllegalArgumentException("Room not found"));

                    existing.setMovie(movie);
                    existing.setRoom(room);
                    existing.setStartTime(req.getStartTime());
                    existing.setEndTime(req.getEndTime());
                    existing.setPrice(resolvePrice(req));

                    Showtime updated = showtimeRepository.save(existing);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // DELETE showtime
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteShowtime(@PathVariable Long id) {
        if (showtimeRepository.existsById(id)) {
            showtimeRepository.deleteById(id);
            return ResponseEntity.ok("Showtime deleted");
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    private Double resolvePrice(ShowtimeRequest req) {
        if (req.getPrice() != null && req.getPrice() > 0) {
            return req.getPrice();
        }

        if (req.getStartTime() == null) {
            return WEEKDAY_PRICE;
        }

        DayOfWeek day = req.getStartTime().getDayOfWeek();
        boolean isWeekday = day.getValue() >= DayOfWeek.MONDAY.getValue() && day.getValue() <= DayOfWeek.THURSDAY.getValue();
        return isWeekday ? WEEKDAY_PRICE : WEEKEND_PRICE;
    }
}
