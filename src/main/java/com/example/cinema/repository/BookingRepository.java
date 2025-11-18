package com.example.cinema.repository;

import com.example.cinema.domain.Booking;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

       // Dành cho Admin xem toàn bộ hoặc theo user
       @EntityGraph(attributePaths = { "customer.user", "showtime.movie", "showtime.room", "seat" })
       List<Booking> findAll();

       @EntityGraph(attributePaths = { "customer.user", "showtime.movie", "showtime.room", "seat" })
       List<Booking> findByCustomer_User_Username(String username);

       List<Booking> findBySoldByStaff_User_UsernameAndStatus(String username, Booking.Status status);

       @EntityGraph(attributePaths = { "customer.user", "showtime.movie", "showtime.room", "seat" })
       List<Booking> findByShowtime_ShowtimeId(Long showtimeId);

       boolean existsByShowtime_ShowtimeIdAndSeat_SeatId(Long showtimeId, Long seatId);

       List<Booking> findByStatus(Booking.Status status);

       // Dùng cho nhóm vé VNPay (multi booking)
       List<Booking> findByTxnRef(String txnRef);

       List<Booking> findByTxnRefAndStatus(String txnRef, Booking.Status status);

       List<Booking> findAllByStatusAndCreatedAtBefore(Booking.Status status, LocalDateTime timestamp);

       // ===================== THỐNG KÊ =====================
       @Query("""
                      SELECT FUNCTION('DATE_FORMAT', b.createdAt, '%Y-%m') AS month,
                             SUM(b.total - COALESCE(b.pointsUsed, 0) * 1000) AS totalRevenue
                      FROM Booking b
                      WHERE b.status = 'PAID'
                      GROUP BY FUNCTION('DATE_FORMAT', b.createdAt, '%Y-%m')
                      ORDER BY month
                     """)
       List<Object[]> getMonthlyRevenue();

       @Query("""
                      SELECT MONTH(b.showtime.startTime) AS month,
                             SUM(b.total - COALESCE(b.pointsUsed, 0) * 1000) AS revenue
                      FROM Booking b
                      WHERE b.status = 'PAID'
                        AND YEAR(b.showtime.startTime) = :year
                      GROUP BY MONTH(b.showtime.startTime)
                      ORDER BY month
                     """)
       List<Object[]> findMonthlyRevenueByYear(@Param("year") int year);

       @Query("""
                     SELECT m.title AS movieTitle,
                            SUM(b.total - COALESCE(b.pointsUsed, 0) * 1000) AS revenue
                     FROM Booking b
                     JOIN b.showtime.movie m
                     WHERE b.status = com.example.cinema.domain.Booking$Status.PAID
                     GROUP BY m.title
                     ORDER BY revenue DESC
                     """)
       List<Object[]> getRevenueByMovie();

       @Query("""
                     SELECT s.user.fullName AS staffName,
                            SUM(b.total - COALESCE(b.pointsUsed, 0) * 1000) AS totalRevenue
                     FROM Booking b
                     JOIN b.soldByStaff s
                     WHERE b.status = com.example.cinema.domain.Booking$Status.PAID
                     GROUP BY s.staffId, s.user.fullName
                     ORDER BY totalRevenue DESC
                     """)
       List<Object[]> getRevenueByStaff();
}
