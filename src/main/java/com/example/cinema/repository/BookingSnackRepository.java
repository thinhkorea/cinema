package com.example.cinema.repository;

import com.example.cinema.domain.BookingSnack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingSnackRepository extends JpaRepository<BookingSnack, Long> {

    List<BookingSnack> findByBooking_BookingId(Long bookingId);

    @Query("SELECT bs FROM BookingSnack bs WHERE bs.booking.txnRef = :txnRef")
    List<BookingSnack> findByTxnRef(@Param("txnRef") String txnRef);

    @Query("""
           SELECT FUNCTION('DATE_FORMAT', bs.booking.createdAt, '%Y-%m') AS month,
                  SUM(bs.priceAtPurchase * bs.quantity) AS revenue
           FROM BookingSnack bs
           WHERE bs.booking.status = com.example.cinema.domain.Booking$Status.PAID
           GROUP BY FUNCTION('DATE_FORMAT', bs.booking.createdAt, '%Y-%m')
           ORDER BY month
           """)
    List<Object[]> getMonthlySnackRevenue();

    @Query("""
           SELECT MONTH(bs.booking.showtime.startTime) AS month,
                  SUM(bs.priceAtPurchase * bs.quantity) AS revenue
           FROM BookingSnack bs
           WHERE bs.booking.status = com.example.cinema.domain.Booking$Status.PAID
             AND YEAR(bs.booking.showtime.startTime) = :year
           GROUP BY MONTH(bs.booking.showtime.startTime)
           ORDER BY month
           """)
    List<Object[]> findMonthlySnackRevenueByYear(@Param("year") int year);

    @Query("""
           SELECT bs.booking.showtime.movie.title AS movieTitle,
                  SUM(bs.priceAtPurchase * bs.quantity) AS revenue
           FROM BookingSnack bs
           WHERE bs.booking.status = com.example.cinema.domain.Booking$Status.PAID
           GROUP BY bs.booking.showtime.movie.title
           """)
    List<Object[]> getSnackRevenueByMovie();

    @Query("""
           SELECT bs.booking.soldByStaff.user.fullName AS staffName,
                  SUM(bs.priceAtPurchase * bs.quantity) AS revenue
           FROM BookingSnack bs
           WHERE bs.booking.status = com.example.cinema.domain.Booking$Status.PAID
             AND bs.booking.soldByStaff IS NOT NULL
           GROUP BY bs.booking.soldByStaff.staffId, bs.booking.soldByStaff.user.fullName
           """)
    List<Object[]> getSnackRevenueByStaff();

    @Query("""
           SELECT HOUR(bs.booking.showtime.startTime) AS showtimeHour,
                  MINUTE(bs.booking.showtime.startTime) AS showtimeMinute,
                  SUM(bs.priceAtPurchase * bs.quantity) AS revenue
           FROM BookingSnack bs
           WHERE bs.booking.status = com.example.cinema.domain.Booking$Status.PAID
             AND YEAR(bs.booking.showtime.startTime) = :year
           GROUP BY HOUR(bs.booking.showtime.startTime), MINUTE(bs.booking.showtime.startTime)
           ORDER BY showtimeHour, showtimeMinute
           """)
    List<Object[]> getSnackRevenueByShowtimeTime(@Param("year") int year);

    void deleteByBooking_BookingId(Long bookingId);
}
