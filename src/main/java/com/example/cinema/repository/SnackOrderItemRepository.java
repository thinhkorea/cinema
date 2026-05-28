package com.example.cinema.repository;

import com.example.cinema.domain.SnackOrderItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SnackOrderItemRepository extends JpaRepository<SnackOrderItem, Long> {

    @EntityGraph(attributePaths = {"snack"})
    List<SnackOrderItem> findBySnackOrder_SnackOrderIdOrderBySnackOrderItemIdAsc(Long snackOrderId);

    @Query("""
           SELECT FUNCTION('DATE_FORMAT', soi.snackOrder.createdAt, '%Y-%m') AS month,
                  SUM(soi.priceAtPurchase * soi.quantity) AS revenue
           FROM SnackOrderItem soi
           WHERE soi.snackOrder.status = com.example.cinema.domain.SnackOrder$Status.PAID
             AND soi.snackOrder.orderType = com.example.cinema.domain.SnackOrder$OrderType.BOOKING_ATTACHED
           GROUP BY FUNCTION('DATE_FORMAT', soi.snackOrder.createdAt, '%Y-%m')
           ORDER BY month
           """)
    List<Object[]> getMonthlyAttachedSnackRevenue();

    @Query("""
           SELECT MONTH(soi.snackOrder.booking.showtime.startTime) AS month,
                  SUM(soi.priceAtPurchase * soi.quantity) AS revenue
           FROM SnackOrderItem soi
           WHERE soi.snackOrder.status = com.example.cinema.domain.SnackOrder$Status.PAID
             AND soi.snackOrder.orderType = com.example.cinema.domain.SnackOrder$OrderType.BOOKING_ATTACHED
             AND soi.snackOrder.booking IS NOT NULL
             AND YEAR(soi.snackOrder.booking.showtime.startTime) = :year
           GROUP BY MONTH(soi.snackOrder.booking.showtime.startTime)
           ORDER BY month
           """)
    List<Object[]> findMonthlyAttachedSnackRevenueByYear(@Param("year") int year);

    @Query("""
           SELECT soi.snackOrder.booking.showtime.movie.title AS movieTitle,
                  SUM(soi.priceAtPurchase * soi.quantity) AS revenue
           FROM SnackOrderItem soi
           WHERE soi.snackOrder.status = com.example.cinema.domain.SnackOrder$Status.PAID
             AND soi.snackOrder.orderType = com.example.cinema.domain.SnackOrder$OrderType.BOOKING_ATTACHED
             AND soi.snackOrder.booking IS NOT NULL
           GROUP BY soi.snackOrder.booking.showtime.movie.title
           """)
    List<Object[]> getAttachedSnackRevenueByMovie();

    @Query("""
           SELECT soi.snackOrder.booking.soldByStaff.user.fullName AS staffName,
                  SUM(soi.priceAtPurchase * soi.quantity) AS revenue
           FROM SnackOrderItem soi
           WHERE soi.snackOrder.status = com.example.cinema.domain.SnackOrder$Status.PAID
             AND soi.snackOrder.orderType = com.example.cinema.domain.SnackOrder$OrderType.BOOKING_ATTACHED
             AND soi.snackOrder.booking IS NOT NULL
             AND soi.snackOrder.booking.soldByStaff IS NOT NULL
           GROUP BY soi.snackOrder.booking.soldByStaff.staffId, soi.snackOrder.booking.soldByStaff.user.fullName
           """)
    List<Object[]> getAttachedSnackRevenueByStaff();

    @Query("""
           SELECT HOUR(soi.snackOrder.booking.showtime.startTime) AS showtimeHour,
                  MINUTE(soi.snackOrder.booking.showtime.startTime) AS showtimeMinute,
                  SUM(soi.priceAtPurchase * soi.quantity) AS revenue
           FROM SnackOrderItem soi
           WHERE soi.snackOrder.status = com.example.cinema.domain.SnackOrder$Status.PAID
             AND soi.snackOrder.orderType = com.example.cinema.domain.SnackOrder$OrderType.BOOKING_ATTACHED
             AND soi.snackOrder.booking IS NOT NULL
             AND YEAR(soi.snackOrder.booking.showtime.startTime) = :year
           GROUP BY HOUR(soi.snackOrder.booking.showtime.startTime), MINUTE(soi.snackOrder.booking.showtime.startTime)
           ORDER BY showtimeHour, showtimeMinute
           """)
    List<Object[]> getAttachedSnackRevenueByShowtimeTime(@Param("year") int year);
}
