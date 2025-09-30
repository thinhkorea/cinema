package com.example.cinema.controller;

import com.example.cinema.entity.Movie;
import com.example.cinema.repository.MovieRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieRepository movieRepository;

    public MovieController(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    // 📌 Lấy toàn bộ danh sách phim
    @GetMapping
    public List<Movie> getAllMovies() {
        return movieRepository.findAll();
    }

    // 📌 Lấy phim theo ID
    @GetMapping("/{id}")
    public Movie getMovieById(@PathVariable Long id) {
        return movieRepository.findById(id).orElse(null);
    }

    // 📌 Thêm phim mới
    @PostMapping
    public Movie addMovie(@RequestBody Movie movie) {
        return movieRepository.save(movie);
    }

    // 📌 Cập nhật phim theo ID
    @PutMapping("/{id}")
    public Movie updateMovie(@PathVariable Long id, @RequestBody Movie movieDetails) {
        return movieRepository.findById(id).map(movie -> {
            movie.setTitle(movieDetails.getTitle());
            movie.setGenre(movieDetails.getGenre());
            movie.setDuration(movieDetails.getDuration());
            movie.setDescription(movieDetails.getDescription());
            movie.setReleaseDate(movieDetails.getReleaseDate());
            movie.setEndDate(movieDetails.getEndDate());
            movie.setPosterUrl(movieDetails.getPosterUrl());
            movie.setTrailerUrl(movieDetails.getTrailerUrl());
            return movieRepository.save(movie);
        }).orElse(null);
    }

    // 📌 Xóa phim theo ID
    @DeleteMapping("/{id}")
    public String deleteMovie(@PathVariable Long id) {
        if (movieRepository.existsById(id)) {
            movieRepository.deleteById(id);
            return "Deleted movie with ID " + id;
        }
        return "Movie not found!";
    }
}
