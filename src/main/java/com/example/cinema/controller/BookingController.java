package com.example.cinema.controller;

import com.example.cinema.domain.Booking;
import com.example.cinema.dto.BookingRequest;
import com.example.cinema.service.BookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    // --- Lịch sử của chính user (JWT) ---
    @GetMapping("/me")
    public ResponseEntity<List<Booking>> myBookings(Authentication auth) {
        String username = (String) auth.getPrincipal();
        return ResponseEntity.ok(bookingService.findByUsername(username));
    }

    // --- Admin xem theo showtime ---
    @GetMapping("/showtime/{showtimeId}")
    public ResponseEntity<List<Booking>> byShowtime(@PathVariable Long showtimeId) {
        return ResponseEntity.ok(bookingService.findByShowtimeId(showtimeId));
    }

    // --- Sơ đồ ghế suất chiếu: public hoặc có thể cho CUSTOMER ---
    // BookingController.java (thêm endpoint)
    @GetMapping("/showtime/{showtimeId}/seats-status")
    public ResponseEntity<?> seatsStatus(@PathVariable Long showtimeId) {
        try {
            return ResponseEntity.ok(bookingService.seatStatusesForShowtime(showtimeId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // --- Đặt vé (CUSTOMER) ---
    @PostMapping
    public ResponseEntity<?> create(@RequestBody @jakarta.validation.Valid BookingRequest req, Authentication auth) {
        try {
            String username;

            // ✅ Kiểm tra kiểu của principal trước khi ép kiểu
            Object principal = auth.getPrincipal();
            if (principal instanceof org.springframework.security.core.userdetails.User) {
                username = ((org.springframework.security.core.userdetails.User) principal).getUsername();
            } else {
                username = principal.toString();
            }

            Booking booking = bookingService.createBooking(username, req.getShowtimeId(), req.getSeatId());
            return ResponseEntity.ok(booking);

        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // --- Đánh dấu đã thanh toán (ADMIN) ---
    @PostMapping("/{id}/pay")
    public ResponseEntity<?> markPaid(@PathVariable Long id) {
        try {
            Booking b = bookingService.markPaid(id);
            return ResponseEntity.ok(b);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // --- Huỷ vé (CUSTOMER: chỉ huỷ vé của mình; ADMIN: huỷ tất cả) ---
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id, Authentication auth) {
        try {
            String username = auth.getName(); // ✅ sửa chỗ này
            boolean isAdmin = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            Booking booking = bookingService.cancel(id, username, isAdmin);
            return ResponseEntity.ok(booking);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

}
