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

    // Initialize seats for a room (128 standard seats, optional sweetbox to 144)
    public void initializeSeatsForRoom(Room room) {
        // Check if seats already exist for this room
        List<Seat> existingSeats = seatRepo.findByRoom_RoomId(room.getRoomId());
        if (!existingSeats.isEmpty()) {
            return; // Seats already exist
        }

        // Create seats: A-H rows, 1-16 columns
        List<Seat> seatsToCreate = new ArrayList<>();
        String[] rows = {"A", "B", "C", "D", "E", "F", "G", "H"};

        for (String row : rows) {
            for (int col = 1; col <= 16; col++) {
                Seat seat = new Seat();
                seat.setRoom(room);
                seat.setSeatNumber(row + col);
                // 3 hàng đầu (A, B, C) là NORMAL; các hàng còn lại là VIP
                seat.setSeatType((row.equals("A") || row.equals("B") || row.equals("C"))
                        ? Seat.SeatType.NORMAL
                        : Seat.SeatType.VIP);
                seat.setBooking(false);
                seatsToCreate.add(seat);
            }
        }

        // Nếu phòng có capacity >= 144 thì bổ sung 8 ghế đôi (row I)
        if (room.getCapacity() != null && room.getCapacity() >= 144) {
            for (int col = 1; col <= 16; col += 2) {
                Seat sweetbox = new Seat();
                sweetbox.setRoom(room);
                sweetbox.setSeatNumber("I" + col + "-" + (col + 1));
                sweetbox.setSeatType(Seat.SeatType.SWEETBOX);
                sweetbox.setBooking(false);
                seatsToCreate.add(sweetbox);
            }
        }

        // Save all seats
        seatRepo.saveAll(seatsToCreate);
    }
}
