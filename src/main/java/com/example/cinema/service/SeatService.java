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

    // Initialize seats for a room (10 rows x 10 columns)
    public void initializeSeatsForRoom(Room room) {
        // Check if seats already exist for this room
        List<Seat> existingSeats = seatRepo.findByRoom_RoomId(room.getRoomId());
        if (!existingSeats.isEmpty()) {
            return; // Seats already exist
        }

        // Create seats: A-J rows, 1-10 columns
        List<Seat> seatsToCreate = new ArrayList<>();
        String[] rows = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J"};

        for (String row : rows) {
            for (int col = 1; col <= 10; col++) {
                Seat seat = new Seat();
                seat.setRoom(room);
                seat.setSeatNumber(row + col);
                seat.setSeatType(Seat.SeatType.NORMAL); // Default to NORMAL
                seat.setBooking(false);
                seatsToCreate.add(seat);
            }
        }

        // Save all seats
        seatRepo.saveAll(seatsToCreate);
    }
}
