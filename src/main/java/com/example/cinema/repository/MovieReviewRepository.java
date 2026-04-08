package com.example.cinema.repository;

import com.example.cinema.domain.MovieReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MovieReviewRepository extends JpaRepository<MovieReview, Long> {

    List<MovieReview> findByMovie_MovieIdOrderByCreatedAtDesc(Long movieId);

    Optional<MovieReview> findByMovie_MovieIdAndUser_UserId(Long movieId, Long userId);

    long countByMovie_MovieId(Long movieId);

    @Query("select avg(r.rating) from MovieReview r where r.movie.movieId = :movieId")
    Double findAverageRatingByMovieId(Long movieId);
}
