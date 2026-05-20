package com.example.cinema.controller;

import com.example.cinema.domain.Movie;
import com.example.cinema.domain.Room;
import com.example.cinema.domain.Showtime;
import com.example.cinema.dto.ShowtimeRequestDTO;
import com.example.cinema.repository.MovieRepository;
import com.example.cinema.repository.RoomRepository;
import com.example.cinema.repository.ShowtimeRepository;
import com.example.cinema.service.SeatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/showtimes")
public class ShowtimeController {

    private static final double WEEKDAY_PRICE = 65000.0;
    private static final double WEEKEND_PRICE = 80000.0;
    private static final LocalTime OPENING_TIME = LocalTime.of(8, 30);
    private static final LocalTime LAST_SHOWTIME_START = LocalTime.of(23, 59);
    private static final int ROOM_TURNAROUND_MINUTES = 10;
    private static final DateTimeFormatter SHOWTIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

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
    public ResponseEntity<?> createShowtime(@RequestBody ShowtimeRequestDTO req) {
        String validationError = validateShowtimeTime(req);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }

        Movie movie = movieRepository.findById(req.getMovieId())
                .orElseThrow(() -> new IllegalArgumentException("Movie not found"));
        Room room = roomRepository.findById(req.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));
        validationError = validateRoomAvailability(req, null);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }

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
    public ResponseEntity<?> updateShowtime(@PathVariable Long id, @RequestBody ShowtimeRequestDTO req) {
        String validationError = validateShowtimeTime(req);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }
        validationError = validateRoomAvailability(req, id);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }

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

    private Double resolvePrice(ShowtimeRequestDTO req) {
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

    private String validateShowtimeTime(ShowtimeRequestDTO req) {
        if (req == null || req.getStartTime() == null || req.getEndTime() == null) {
            return "Vui lòng chọn đầy đủ giờ bắt đầu và giờ kết thúc suất chiếu.";
        }

        LocalDateTime startTime = req.getStartTime();
        LocalDateTime endTime = req.getEndTime();
        if (startTime.isBefore(LocalDateTime.now())) {
            return "Không thể tạo suất chiếu có giờ bắt đầu nhỏ hơn thời điểm hiện tại.";
        }
        if (!endTime.isAfter(startTime)) {
            return "Giờ kết thúc phải sau giờ bắt đầu.";
        }

        LocalTime start = startTime.toLocalTime();
        if (start.isBefore(OPENING_TIME) || start.isAfter(LAST_SHOWTIME_START)) {
            return "Suất chiếu chỉ được bắt đầu trong khung giờ hoạt động của rạp: 08:30 - 23:59.";
        }

        return null;
    }

    private String validateRoomAvailability(ShowtimeRequestDTO req, Long excludeShowtimeId) {
        if (req.getRoomId() == null) {
            return "Vui lòng chọn phòng chiếu.";
        }

        List<Showtime> conflicts = showtimeRepository.findOverlappingShowtimes(
                req.getRoomId(),
                req.getStartTime().minusMinutes(ROOM_TURNAROUND_MINUTES),
                req.getEndTime().plusMinutes(ROOM_TURNAROUND_MINUTES),
                excludeShowtimeId);
        if (conflicts.isEmpty()) {
            return null;
        }

        Showtime conflict = conflicts.get(0);
        String movieTitle = conflict.getMovie() != null ? conflict.getMovie().getTitle() : "phim khác";
        String roomName = conflict.getRoom() != null ? conflict.getRoom().getRoomName() : "phòng này";
        return "Phòng " + roomName + " đã có suất chiếu " + movieTitle
                + " từ " + conflict.getStartTime().format(SHOWTIME_FORMAT)
                + " đến " + conflict.getEndTime().format(SHOWTIME_FORMAT)
                + ". Phòng cần nghỉ ít nhất " + ROOM_TURNAROUND_MINUTES
                + " phút giữa hai suất. Vui lòng chọn phòng hoặc thời gian khác.";
    }
}
