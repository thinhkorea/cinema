package com.example.cinema.repository;

import com.example.cinema.domain.Booking;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("""
                SELECT
                    FUNCTION('DATE_FORMAT', b.createdAt, '%Y-%m') AS month,
                    SUM(s.price) AS totalRevenue
                FROM Booking b
                JOIN b.showtime s
                WHERE b.status = 'PAID'
                GROUP BY FUNCTION('DATE_FORMAT', b.createdAt, '%Y-%m')
                ORDER BY month
            """)
    List<Object[]> getMonthlyRevenue();

    @Query("""
                SELECT MONTH(b.showtime.startTime) AS month,
                       SUM(b.showtime.price) AS revenue
                FROM Booking b
                WHERE b.status = 'PAID'
                  AND YEAR(b.showtime.startTime) = :year
                GROUP BY MONTH(b.showtime.startTime)
                ORDER BY month
            """)
    List<Object[]> findMonthlyRevenueByYear(@Param("year") int year);

}
