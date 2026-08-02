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
    private final CinemaQdrantService qdrantService;

    public MovieService(MovieRepository movieRepo, CinemaQdrantService qdrantService) {
        this.movieRepo = movieRepo;
        this.qdrantService = qdrantService;
    }

    public List<Movie> findAll() {
        return movieRepo.findAll();
    }

    public Optional<Movie> findById(Long id) {
        return movieRepo.findById(id);
    }

    public Movie save(Movie movie) {
        movie.setSearchEmbedding(null);
        return movieRepo.save(movie);
    }

    public Movie update(Long id, Movie updated) {
        return movieRepo.findById(id)
                .map(existing -> {
                    boolean searchableFieldsChanged = false;

                    if (updated.getTitle() != null) {
                        existing.setTitle(updated.getTitle());
                        searchableFieldsChanged = true;
                    }
                    if (updated.getDuration() != null) {
                        existing.setDuration(updated.getDuration());
                    }
                    if (updated.getGenre() != null) {
                        existing.setGenre(updated.getGenre());
                        searchableFieldsChanged = true;
                    }
                    if (updated.getDescription() != null) {
                        existing.setDescription(updated.getDescription());
                        searchableFieldsChanged = true;
                    }
                    if (updated.getPosterUrl() != null) {
                        existing.setPosterUrl(updated.getPosterUrl());
                    }
                    if (updated.getTrailerUrl() != null) {
                        existing.setTrailerUrl(updated.getTrailerUrl());
                    }
                    if (updated.getStatus() != null) {
                        existing.setStatus(updated.getStatus());
                        searchableFieldsChanged = true;
                    }
                    if (updated.getAgeRating() != null) {
                        existing.setAgeRating(updated.getAgeRating());
                    }
                    if (updated.getActors() != null) {
                        existing.setActors(updated.getActors());
                        searchableFieldsChanged = true;
                    }
                    if (searchableFieldsChanged) {
                        existing.setSearchEmbedding(null);
                        qdrantService.deleteDocument("MOVIE", existing.getMovieId(), "MOVIE:" + existing.getMovieId());
                    }
                    return movieRepo.save(existing);
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
