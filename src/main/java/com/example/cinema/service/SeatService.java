package com.example.cinema.service;

import com.example.cinema.domain.*;
import com.example.cinema.dto.SeatStatusDTO;
import com.example.cinema.repository.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SeatService {
    private final SeatRepository seatRepo;
    private final ShowtimeRepository showtimeRepo;
    private final BookingRepository bookingRepo;

    public SeatService(SeatRepository seatRepo, ShowtimeRepository showtimeRepo, BookingRepository bookingRepo) {
        this.seatRepo = seatRepo;
        this.showtimeRepo = showtimeRepo;
        this.bookingRepo = bookingRepo;
    }

    public List<Seat> findAll() {
        return seatRepo.findAll();
    }

    public Optional<Seat> findById(Long id) {
        return seatRepo.findById(id);
    }

    public List<Seat> findByRoom(Long roomId) {
        return seatRepo.findByRoom_RoomId(roomId);
    }

    public Seat save(Seat seat) {
        return seatRepo.save(seat);
    }

    public void delete(Long id) {
        seatRepo.deleteById(id);
    }

    // Lấy ghế theo suất chiếu
    public List<SeatStatusDTO> getSeatsByShowtime(Long showtimeId) {
        Showtime showtime = showtimeRepo.findById(showtimeId)
                .orElseThrow(() -> new RuntimeException("Showtime not found"));

        // Lấy toàn bộ ghế của phòng chiếu
        List<Seat> seats = seatRepo.findByRoom_RoomId(showtime.getRoom().getRoomId());

        // Lấy danh sách booking của suất chiếu đó
        List<Booking> bookings = bookingRepo.findByShowtime_ShowtimeId(showtimeId);

        // Map seatId -> Booking
        Map<Long, Booking> bookingMap = bookings.stream()
                .collect(Collectors.toMap(b -> b.getSeat().getSeatId(), b -> b));

        // Trả về danh sách SeatStatusDTO
        return seats.stream()
                .map(seat -> {
                    Booking booking = bookingMap.get(seat.getSeatId());
                    boolean booked = booking != null;
                    String status = booked ? booking.getStatus().name() : null;
                    Long bookingId = booked ? booking.getBookingId() : null;

                    return new SeatStatusDTO(seat.getSeatId(), seat.getSeatNumber(), booked, status, bookingId,
                            seat.getSeatType().name());
                })
                .collect(Collectors.toList());
    }
}
