package com.example.cinema.service;

import com.example.cinema.domain.*;
import com.example.cinema.dto.SeatStatusDTO;
import com.example.cinema.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatQueryService {

        private final ShowtimeRepository showtimeRepo;
        private final SeatRepository seatRepo;
        private final BookingRepository bookingRepo;

        /**
         * Lấy danh sách ghế cho một suất chiếu cụ thể,
         * gồm trạng thái PENDING / PAID / AVAILABLE + loại ghế (NORMAL, VIP, SWEETBOX).
         */
        public List<SeatStatusDTO> getSeatsByShowtime(Long showtimeId) {
                // Lấy thông tin suất chiếu (để biết phòng)
                Showtime showtime = showtimeRepo.findById(showtimeId)
                                .orElseThrow(() -> new RuntimeException("Showtime not found"));

                Long roomId = showtime.getRoom().getRoomId();

                // Lấy toàn bộ ghế trong phòng đó
                List<Seat> seats = seatRepo.findByRoom_RoomId(roomId);

                // Lấy tất cả booking thuộc suất chiếu đó
                List<Booking> bookings = bookingRepo.findByShowtime_ShowtimeId(showtimeId);

                // Map seatId -> booking tương ứng
                Map<Long, Booking> bookingMap = bookings.stream()
                                .collect(Collectors.toMap(b -> b.getSeat().getSeatId(), b -> b));

                // Map sang DTO (thêm seatType)
                return seats.stream()
                                .map(seat -> {
                                        Booking booking = bookingMap.get(seat.getSeatId());
                                        boolean booked = booking != null;
                                        String status = booked ? booking.getStatus().name() : null;
                                        Long bookingId = booked ? booking.getBookingId() : null;

                                        return new SeatStatusDTO(
                                                        seat.getSeatId(),
                                                        seat.getSeatNumber(),
                                                        booked,
                                                        status,
                                                        bookingId,
                                                        seat.getSeatType().name() // Thêm loại ghế
                                        );
                                })
                                .collect(Collectors.toList());
        }
}
