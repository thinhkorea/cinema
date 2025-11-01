package com.example.cinema.repository;

import com.example.cinema.domain.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {
    // Tìm phim theo trạng thái (sử dụng inner enum Movie.MovieStatus)
    List<Movie> findByStatus(Movie.MovieStatus status);

    List<Movie> findByGenre(String genre);
}
