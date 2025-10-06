package com.example.cinema.repository;

import com.example.cinema.domain.Booking;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = { "user", "showtime.movie", "showtime.room", "seat" })
    List<Booking> findAll();

    @EntityGraph(attributePaths = { "user", "showtime.movie", "showtime.room", "seat" })
    List<Booking> findByUser_UserId(Long userId);

    @EntityGraph(attributePaths = { "user", "showtime.movie", "showtime.room", "seat" })
    List<Booking> findByShowtime_ShowtimeId(Long showtimeId);

    boolean existsByShowtime_ShowtimeIdAndSeat_SeatId(Long showtimeId, Long seatId);

    Optional<Booking> findByShowtime_ShowtimeIdAndSeat_SeatId(Long showtimeId, Long seatId);

    @EntityGraph(attributePaths = { "user", "showtime.movie", "showtime.room", "seat" })
    List<Booking> findByUser_Username(String username);
}
