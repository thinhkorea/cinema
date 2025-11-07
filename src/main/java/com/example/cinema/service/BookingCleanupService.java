package com.example.cinema.service;

import com.example.cinema.domain.Booking;
import com.example.cinema.repository.BookingRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingCleanupService {

    private final BookingRepository bookingRepository;

    // Thời gian giữ ghế (phút)
    private static final int PENDING_BOOKING_TIMEOUT_MINUTES = 1;

    public BookingCleanupService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    /**
     * Tác vụ này chạy định kỳ để dọn dẹp các booking "treo" (PENDING) đã quá hạn.
     * fixedRate = 300000 ms = 5 phút.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanupPendingBookings() {
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(PENDING_BOOKING_TIMEOUT_MINUTES);

        List<Booking> expiredBookings = bookingRepository.findAllByStatusAndCreatedAtBefore(Booking.Status.PENDING,
                timeoutThreshold);

        if (!expiredBookings.isEmpty()) {
            System.out.println("Dọn dẹp " + expiredBookings.size() + " booking quá hạn...");
            bookingRepository.deleteAll(expiredBookings);
        }
    }
}
