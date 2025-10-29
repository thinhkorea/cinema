package com.example.cinema.repository;

import com.example.cinema.domain.Movie;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.cinema.domain.Showtime;

@Repository
public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {
  List<Showtime> findByMovie_MovieId(Long movieId);

  List<Showtime> findByMovie_MovieIdOrderByStartTimeAsc(Long movieId);

  @Query("SELECT DISTINCT s.movie FROM Showtime s WHERE s.startTime > CURRENT_TIMESTAMP")
  List<Movie> findUpcomingMovies();

  @Query("""
          SELECT DISTINCT DATE(s.startTime)
          FROM Showtime s
                      WHERE s.movie.movieId = :movieId
            AND s.startTime >= CURRENT_DATE
          ORDER BY DATE(s.startTime)
      """)
  List<String> findShowDatesByMovie(@Param("movieId") Long movieId);

  @Query("""
        SELECT s
        FROM Showtime s
        WHERE s.movie.movieId = :movieId
          AND DATE(s.startTime) = DATE(:dateStr)
        ORDER BY s.startTime
      """)
  List<Showtime> findByMovieAndDate(
      @Param("movieId") Long movieId,
      @Param("dateStr") String dateStr);
}
