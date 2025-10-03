package com.example.cinema.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.cinema.domain.Movie;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {
    java.util.List<Movie> findByGenre(String genre);
}
