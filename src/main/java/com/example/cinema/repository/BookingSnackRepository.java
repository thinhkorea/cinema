package com.example.cinema.repository;

import com.example.cinema.domain.BookingSnack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingSnackRepository extends JpaRepository<BookingSnack, Long> {
    
    /**
     * Tìm tất cả snacks của một booking
     */
    List<BookingSnack> findByBooking_BookingId(Long bookingId);
    
    /**
     * Tìm tất cả snacks của các bookings trong một transaction
     */
    @Query("SELECT bs FROM BookingSnack bs WHERE bs.booking.txnRef = :txnRef")
    List<BookingSnack> findByTxnRef(@Param("txnRef") String txnRef);
    
    /**
     * Xóa tất cả snacks của một booking
     */
    void deleteByBooking_BookingId(Long bookingId);
}
