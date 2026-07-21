package com.example.cinema.repository;

import com.example.cinema.domain.Movie;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.cinema.domain.Showtime;

@Repository
public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {
  @Query("SELECT s FROM Showtime s WHERE s.room.active = true OR s.room.active IS NULL")
  List<Showtime> findAllWithActiveRoom();

  @Query("""
      SELECT s
      FROM Showtime s
      WHERE s.startTime > :now
        AND (s.room.active = true OR s.room.active IS NULL)
      ORDER BY s.startTime ASC
      """)
  List<Showtime> findUpcomingWithActiveRoom(@Param("now") LocalDateTime now);

  @Query("""
      SELECT s
      FROM Showtime s
      WHERE s.movie.movieId = :movieId
        AND (s.room.active = true OR s.room.active IS NULL)
      """)
  List<Showtime> findByMovie_MovieId(@Param("movieId") Long movieId);

  @Query("""
      SELECT s
      FROM Showtime s
      WHERE s.movie.movieId = :movieId
        AND (s.room.active = true OR s.room.active IS NULL)
      ORDER BY s.startTime ASC
      """)
  List<Showtime> findByMovie_MovieIdOrderByStartTimeAsc(@Param("movieId") Long movieId);

  @Query("""
      SELECT s
      FROM Showtime s
      WHERE s.movie.movieId = :movieId
        AND s.startTime > :now
        AND (s.room.active = true OR s.room.active IS NULL)
      ORDER BY s.startTime ASC
      """)
  List<Showtime> findUpcomingByMovieIdOrderByStartTimeAsc(
      @Param("movieId") Long movieId,
      @Param("now") LocalDateTime now);

  @Query("SELECT DISTINCT s.movie FROM Showtime s WHERE s.startTime > CURRENT_TIMESTAMP AND (s.room.active = true OR s.room.active IS NULL)")
  List<Movie> findUpcomingMovies();

  @Query("""
          SELECT DISTINCT DATE(s.startTime)
          FROM Showtime s
                      WHERE s.movie.movieId = :movieId
            AND s.startTime >= CURRENT_DATE
            AND (s.room.active = true OR s.room.active IS NULL)
          ORDER BY DATE(s.startTime)
      """)
  List<String> findShowDatesByMovie(@Param("movieId") Long movieId);

  @Query("""
        SELECT s
        FROM Showtime s
        WHERE s.movie.movieId = :movieId
          AND DATE(s.startTime) = DATE(:dateStr)
          AND (s.room.active = true OR s.room.active IS NULL)
        ORDER BY s.startTime
      """)
  List<Showtime> findByMovieAndDate(
      @Param("movieId") Long movieId,
      @Param("dateStr") String dateStr);

  @Query("""
        SELECT s
        FROM Showtime s
        WHERE s.room.roomId = :roomId
          AND (:excludeShowtimeId IS NULL OR s.showtimeId <> :excludeShowtimeId)
          AND s.startTime < :endTime
          AND s.endTime > :startTime
        ORDER BY s.startTime
      """)
  List<Showtime> findOverlappingShowtimes(
      @Param("roomId") Long roomId,
      @Param("startTime") LocalDateTime startTime,
      @Param("endTime") LocalDateTime endTime,
      @Param("excludeShowtimeId") Long excludeShowtimeId);

  @Query("""
        SELECT s
        FROM Showtime s
        WHERE s.startTime = :startTime
          AND (:excludeShowtimeId IS NULL OR s.showtimeId <> :excludeShowtimeId)
        ORDER BY s.startTime
      """)
  List<Showtime> findSameStartTime(
      @Param("startTime") LocalDateTime startTime,
      @Param("excludeShowtimeId") Long excludeShowtimeId);

  @Query("""
        SELECT s
        FROM Showtime s
        WHERE s.startTime BETWEEN :windowStart AND :windowEnd
          AND (:excludeShowtimeId IS NULL OR s.showtimeId <> :excludeShowtimeId)
        ORDER BY s.startTime
      """)
  List<Showtime> findNearbyStartTimes(
      @Param("windowStart") LocalDateTime windowStart,
      @Param("windowEnd") LocalDateTime windowEnd,
      @Param("excludeShowtimeId") Long excludeShowtimeId);
}
