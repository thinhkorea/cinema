package com.example.cinema.controller;

import com.example.cinema.domain.Booking;
import com.example.cinema.dto.BookingRequest;
import com.example.cinema.dto.SoldTicketDTO;
import com.example.cinema.service.BookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/bookings")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    // ==================== ADMIN ====================
    @GetMapping
    public ResponseEntity<?> getAllBookings() {
        return ResponseEntity.ok(bookingService.findAllDTO());
    }

    @GetMapping("/showtime/{showtimeId}")
    public ResponseEntity<List<Booking>> byShowtime(@PathVariable Long showtimeId) {
        return ResponseEntity.ok(bookingService.findByShowtimeId(showtimeId));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<?> getByStatus(@PathVariable String status) {
        try {
            Booking.Status s = Booking.Status.valueOf(status.toUpperCase());
            return ResponseEntity.ok(bookingService.findByStatus(s));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status"));
        }
    }

    @GetMapping("/revenue/monthly")
    public ResponseEntity<?> getMonthlyRevenue() {
        return ResponseEntity.ok(bookingService.getMonthlyRevenue());
    }

    @GetMapping("/revenue/year/{year}")
    public ResponseEntity<?> getMonthlyRevenueByYear(@PathVariable int year) {
        return ResponseEntity.ok(bookingService.getMonthlyRevenueByYear(year));
    }

    @GetMapping("/revenue/movie")
    public ResponseEntity<?> getRevenueByMovie() {
        return ResponseEntity.ok(bookingService.getRevenueByMovie());
    }

    @GetMapping("/admin/revenue/staffs")
    public ResponseEntity<?> getRevenueByStaffs() {
        return ResponseEntity.ok(bookingService.getRevenueByStaff());
    }

    // ==================== STAFF / VNPay ====================
    @PostMapping("/staff/sell-multi")
    @Deprecated // Endpoint này sẽ được thay thế bằng /staff/create-multi
    public ResponseEntity<?> sellMulti(@RequestBody BookingRequest req) {
        return createMultiByStaff(req); // Chuyển hướng logic sang phương thức createMultiByStaff
    }

    @PostMapping("/pay-by-txn/{txnRef}")
    public ResponseEntity<?> markPaidByTxn(@PathVariable String txnRef) {
        try {
            bookingService.markPaidByTxn(txnRef, "VNPAY"); // Thêm phương thức thanh toán là VNPAY
            return ResponseEntity.ok(Map.of("message", "Đã cập nhật thanh toán thành công!", "txnRef", txnRef));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== STAFF / CASH ====================
    // B1: Nhân viên chọn ghế và tạo nhiều booking (chưa thanh toán)
    @PostMapping("/staff/create-multi")
    public ResponseEntity<?> createMultiByStaff(@RequestBody BookingRequest req) {
        try {
            if (req.getSeatIds() == null || req.getSeatIds().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Chưa chọn ghế nào!"));
            }
            if (req.getStaffUsername() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Không tìm thấy thông tin nhân viên."));
            }

            // Tạo txnRef để nhóm các booking cùng lượt bán
            String txnRef = String.valueOf(System.currentTimeMillis()); // Sửa lỗi suy luận kiểu
            List<Booking> bookings = bookingService.createMultiBooking(req.getShowtimeId(), req.getSeatIds(), txnRef,
                    req.getStaffUsername());

            return ResponseEntity.ok(Map.of(
                    "message", "Đã tạo " + bookings.size() + " booking, chờ thanh toán!",
                    "txnRef", txnRef));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // B2: Thanh toán tiền mặt toàn bộ booking cùng txnRef
    @PostMapping("/pay-cash/{txnRef}")
    public ResponseEntity<?> payCash(@PathVariable String txnRef) {
        try { // Không cần truyền username ở đây vì booking đã có sẵn thông tin staff
            bookingService.markPaidByTxn(txnRef, "CASH");
            return ResponseEntity.ok(Map.of(
                    "message", "Thanh toán tiền mặt thành công!",
                    "txnRef", txnRef));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/staff/sold")
    public ResponseEntity<?> getSoldByStaff(@RequestParam String username) {
        // Chỉ lấy các booking đã thanh toán (PAID) do nhân viên này bán
        List<SoldTicketDTO> soldTickets = bookingService.findSoldTicketsByStaffUsername(username);
        return ResponseEntity.ok(soldTickets);
    }
}
