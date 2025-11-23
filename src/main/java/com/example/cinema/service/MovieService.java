package com.example.cinema.service;

import com.example.cinema.domain.Movie;
import com.example.cinema.repository.MovieRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class MovieService {

    private final MovieRepository movieRepo;

    public MovieService(MovieRepository movieRepo) {
        this.movieRepo = movieRepo;
    }

    public List<Movie> findAll() {
        return movieRepo.findAll();
    }

    public Optional<Movie> findById(Long id) {
        return movieRepo.findById(id);
    }

    public Movie save(Movie movie) {
        return movieRepo.save(movie);
    }

    public Movie update(Long id, Movie updated) {
        System.out.println("=== UPDATE MOVIE ===");
        System.out.println("ID: " + id);
        System.out.println("Updated Movie: " + updated);
        System.out.println("Title: " + updated.getTitle());
        System.out.println("Status: " + updated.getStatus());
        System.out.println("AgeRating: " + updated.getAgeRating());
        System.out.println("Actors: " + updated.getActors());
        System.out.println("Description: " + updated.getDescription());
        
        return movieRepo.findById(id)
                .map(existing -> {
                    System.out.println("Found existing movie: " + existing.getTitle());
                    existing.setTitle(updated.getTitle());
                    existing.setDuration(updated.getDuration());
                    existing.setGenre(updated.getGenre());
                    existing.setDescription(updated.getDescription());
                    existing.setPosterUrl(updated.getPosterUrl());
                    existing.setTrailerUrl(updated.getTrailerUrl());
                    existing.setStatus(updated.getStatus());
                    existing.setAgeRating(updated.getAgeRating());
                    existing.setActors(updated.getActors());
                    System.out.println("Before save - Status: " + existing.getStatus());
                    Movie saved = movieRepo.save(existing);
                    System.out.println("After save - Status: " + saved.getStatus());
                    return saved;
                })
                .orElseThrow(() -> new IllegalArgumentException("Movie not found: " + id));
    }

    public void delete(Long id) {
        movieRepo.deleteById(id);
    }

    public List<Movie> findByStatus(String status) {
        try {
            Movie.MovieStatus movieStatus = Movie.MovieStatus.valueOf(status.toUpperCase());
            return movieRepo.findByStatus(movieStatus);
        } catch (IllegalArgumentException e) {
            // Nếu status không hợp lệ, trả về danh sách rỗng
            return new ArrayList<>();
        }
    }
}
