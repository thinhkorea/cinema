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
    private final RoomRepository roomRepo;

    public SeatService(SeatRepository seatRepo, ShowtimeRepository showtimeRepo, BookingRepository bookingRepo, RoomRepository roomRepo) {
        this.seatRepo = seatRepo;
        this.showtimeRepo = showtimeRepo;
        this.bookingRepo = bookingRepo;
        this.roomRepo = roomRepo;
    }

    public List<Seat> findAll() {
        return seatRepo.findAll().stream()
                .filter(seat -> !Boolean.FALSE.equals(seat.getActive()))
                .collect(Collectors.toList());
    }

    public Optional<Seat> findById(Long id) {
        return seatRepo.findById(id);
    }

    public List<Seat> findByRoom(Long roomId) {
        return seatRepo.findByRoom_RoomIdAndActiveTrueOrderBySeatNumberAsc(roomId);
    }

    public Seat save(Seat seat) {
        return seatRepo.save(seat);
    }

    public void delete(Long id) {
        Seat seat = seatRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Seat not found"));
        seat.setActive(false);
        seatRepo.save(seat);
    }

    public Seat createSeatForRoom(Long roomId, Seat request) {
        Room room = roomRepo.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));
        if (Boolean.FALSE.equals(room.getActive())) {
            throw new IllegalArgumentException("Phòng chiếu đã bị vô hiệu hóa.");
        }
        String seatNumber = normalizeSeatNumber(request.getSeatNumber());
        if (seatRepo.existsByRoom_RoomIdAndSeatNumberIgnoreCaseAndActiveTrue(roomId, seatNumber)) {
            throw new IllegalArgumentException("Số ghế đã tồn tại trong phòng này.");
        }

        Seat seat = new Seat();
        seat.setRoom(room);
        seat.setSeatNumber(seatNumber);
        seat.setSeatType(request.getSeatType() == null ? Seat.SeatType.NORMAL : request.getSeatType());
        seat.setBooking(false);
        seat.setActive(true);
        return seatRepo.save(seat);
    }

    public Seat updateSeat(Long seatId, Seat request) {
        Seat seat = seatRepo.findById(seatId)
                .orElseThrow(() -> new IllegalArgumentException("Seat not found"));
        String seatNumber = normalizeSeatNumber(request.getSeatNumber());
        Long roomId = seat.getRoom().getRoomId();
        if (seatRepo.existsByRoom_RoomIdAndSeatNumberIgnoreCaseAndActiveTrueAndSeatIdNot(roomId, seatNumber, seatId)) {
            throw new IllegalArgumentException("Số ghế đã tồn tại trong phòng này.");
        }

        seat.setSeatNumber(seatNumber);
        seat.setSeatType(request.getSeatType() == null ? Seat.SeatType.NORMAL : request.getSeatType());
        seat.setActive(true);
        return seatRepo.save(seat);
    }

    public void deleteSeat(Long seatId) {
        delete(seatId);
    }

    private String normalizeSeatNumber(String seatNumber) {
        if (seatNumber == null || seatNumber.isBlank()) {
            throw new IllegalArgumentException("Số ghế không được để trống.");
        }
        return seatNumber.trim().toUpperCase(Locale.ROOT);
    }

    // Lấy ghế theo suất chiếu
    public List<SeatStatusDTO> getSeatsByShowtime(Long showtimeId) {
        Showtime showtime = showtimeRepo.findById(showtimeId)
                .orElseThrow(() -> new RuntimeException("Showtime not found"));

        // Lấy toàn bộ ghế của phòng chiếu
        List<Seat> seats = seatRepo.findByRoom_RoomIdAndActiveTrue(showtime.getRoom().getRoomId());

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

                        return SeatStatusDTO.builder()
                            .seatId(seat.getSeatId())
                            .seatNumber(seat.getSeatNumber())
                            .booked(booked)
                            .status(status)
                            .bookingId(bookingId)
                            .seatType(seat.getSeatType().name())
                            .build();
                })
                .collect(Collectors.toList());
    }

    // Initialize seats for a room (128 standard seats, optional sweetbox to 144)
    public void initializeSeatsForRoom(Room room) {
        // Check if seats already exist for this room
        List<Seat> existingSeats = seatRepo.findByRoom_RoomIdAndActiveTrue(room.getRoomId());
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
                seat.setActive(true);
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
                sweetbox.setActive(true);
                seatsToCreate.add(sweetbox);
            }
        }

        // Save all seats
        seatRepo.saveAll(seatsToCreate);
    }
}
