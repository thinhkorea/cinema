package com.example.cinema.controller.staff;

import com.example.cinema.domain.Movie;
import com.example.cinema.domain.Showtime;
import com.example.cinema.repository.ShowtimeRepository;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/staff/showtimes")
public class StaffShowtimeController {

    private final ShowtimeRepository showtimeRepo;

    public StaffShowtimeController(ShowtimeRepository showtimeRepo) {
        this.showtimeRepo = showtimeRepo;
    }

    // Lấy toàn bộ danh sách suất chiếu (cho staff chọn)
    @GetMapping
    public List<Showtime> getAllShowtimes() {
        return showtimeRepo.findAll();
    }

    // Lấy danh sách phim có suất chiếu sắp tới
    @GetMapping("/movies")
    public List<Movie> getAvailableMovies() {
        return showtimeRepo.findUpcomingMovies();
    }

    // Lấy danh sách ngày chiếu có sẵn theo phim
    @GetMapping("/movie/{movieId}/dates")
    public List<String> getAvailableDates(@PathVariable Long movieId) {
        return showtimeRepo.findShowDatesByMovie(movieId);
    }

    // Lấy danh sách suất chiếu theo phim & ngày
    @GetMapping("/movie/{movieId}/date/{date}")
    public List<Showtime> getShowtimesByMovieAndDate(
            @PathVariable Long movieId,
            @PathVariable("date") String date) {
        return showtimeRepo.findByMovieAndDate(movieId, date);
    }

    // Lấy danh sách suất chiếu theo phim (để frontend nhóm theo ngày)
    @GetMapping("/movie/{movieId}")
    public List<Showtime> getShowtimesForMovie(@PathVariable Long movieId) {
        return showtimeRepo.findByMovie_MovieIdOrderByStartTimeAsc(movieId);
    }

}
