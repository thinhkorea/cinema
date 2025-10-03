package com.example.cinema.repository;

import com.example.cinema.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUser_UserId(Long userId);

    List<Booking> findByShowtime_ShowtimeId(Long showtimeId);

    boolean existsByShowtime_ShowtimeIdAndSeat_SeatId(Long showtimeId, Long seatId);

    Optional<Booking> findByShowtime_ShowtimeIdAndSeat_SeatId(Long showtimeId, Long seatId);

    // NEW: lấy theo username (dùng cho /me)
    List<Booking> findByUser_Username(String username);
}
