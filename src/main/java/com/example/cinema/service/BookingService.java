package com.example.cinema.service;

import com.example.cinema.domain.*;
import com.example.cinema.dto.SeatStatusDTO;
import com.example.cinema.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private final BookingRepository bookingRepo;
    private final UserRepository userRepo;
    private final ShowtimeRepository showtimeRepo;
    private final SeatRepository seatRepo;

    public BookingService(BookingRepository bookingRepo,
            UserRepository userRepo,
            ShowtimeRepository showtimeRepo,
            SeatRepository seatRepo) {
        this.bookingRepo = bookingRepo;
        this.userRepo = userRepo;
        this.showtimeRepo = showtimeRepo;
        this.seatRepo = seatRepo;
    }

    public List<Booking> findAll() {
        return bookingRepo.findAll();
    }

    public List<Booking> findByUserId(Long userId) {
        return bookingRepo.findByUser_UserId(userId);
    }

    public List<Booking> findByUsername(String username) {
        return bookingRepo.findByUser_Username(username);
    }

    public List<Booking> findByShowtimeId(Long showtimeId) {
        return bookingRepo.findByShowtime_ShowtimeId(showtimeId);
    }

    @Transactional
    public Booking createBooking(String username, Long showtimeId, Long seatId) {
        // 1) Load user theo username (lấy từ JWT principal)
        User user = userRepo.findByUsername(username);
        if (user == null)
            throw new IllegalArgumentException("User not found: " + username);

        // 2) Load showtime & seat
        Showtime showtime = showtimeRepo.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Showtime not found: " + showtimeId));

        Seat seat = seatRepo.findById(seatId)
                .orElseThrow(() -> new IllegalArgumentException("Seat not found: " + seatId));

        // 3) Validate: seat phải thuộc đúng room của showtime
        if (!seat.getRoom().getRoomId().equals(showtime.getRoom().getRoomId())) {
            throw new IllegalStateException("Seat does not belong to the showtime's room");
        }

        // 4) Validate: không đặt trùng ghế cho cùng suất
        if (bookingRepo.existsByShowtime_ShowtimeIdAndSeat_SeatId(showtimeId, seatId)) {
            throw new IllegalStateException("Seat already booked for this showtime");
        }

        // 5) Lưu booking
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setShowtime(showtime);
        booking.setSeat(seat);
        booking.setStatus(Booking.Status.PENDING);

        try {
            return bookingRepo.save(booking);
        } catch (DataIntegrityViolationException ex) {
            // Phòng khi 2 request race condition, DB unique constraint sẽ bắt
            throw new IllegalStateException("Seat already booked (race condition)", ex);
        }
    }

    @Transactional
    public Booking markPaid(Long bookingId) {
        Booking b = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        b.setStatus(Booking.Status.PAID);
        return bookingRepo.save(b);
    }

    @Transactional
    public Booking cancel(Long bookingId, String username, boolean isAdmin) {
        Booking b = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        // Nếu không phải admin thì chỉ được huỷ vé của chính mình
        if (!isAdmin && !b.getUser().getUsername().equals(username)) {
            throw new SecurityException("Not allowed to cancel this booking");
        }
        b.setStatus(Booking.Status.CANCELLED);
        return bookingRepo.save(b);
    }

    @Transactional
    public void deleteHard(Long bookingId) {
        bookingRepo.deleteById(bookingId);
    }

    // NEW: build sơ đồ ghế + trạng thái cho 1 showtime
    public List<SeatStatusDTO> seatStatusesForShowtime(Long showtimeId) {
        Showtime showtime = showtimeRepo.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Showtime not found: " + showtimeId));

        // tất cả ghế của phòng
        List<Seat> seats = seatRepo.findByRoom_RoomId(showtime.getRoom().getRoomId());

        // các booking của suất này -> map theo seatId
        Map<Long, Booking> bookingBySeatId = bookingRepo.findByShowtime_ShowtimeId(showtimeId)
                .stream()
                // nếu có nhiều trạng thái, ưu tiên hiển thị bản ghi mới nhất hoặc PAID; ở đây
                // chọn bản ghi đầu tiên
                .collect(Collectors.toMap(b -> b.getSeat().getSeatId(), b -> b, (b1, b2) -> b1));

        List<SeatStatusDTO> result = new ArrayList<>(seats.size());
        for (Seat s : seats) {
            Booking booked = bookingBySeatId.get(s.getSeatId());
            if (booked != null && booked.getStatus() != Booking.Status.CANCELLED) {
                result.add(new SeatStatusDTO(
                        s.getSeatId(),
                        s.getSeatNumber(),
                        true,
                        booked.getStatus().name(),
                        booked.getBookingId()));
            } else {
                result.add(new SeatStatusDTO(
                        s.getSeatId(),
                        s.getSeatNumber(),
                        false,
                        null,
                        null));
            }
        }
        return result;
    }
}
