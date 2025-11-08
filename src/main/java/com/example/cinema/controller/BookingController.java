package com.example.cinema.controller;

import com.example.cinema.domain.Booking;
import com.example.cinema.dto.BookingRequest;
import com.example.cinema.dto.SoldTicketDTO;
import com.example.cinema.service.BookingService;
import com.example.cinema.service.TicketPDFService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.stream.Collectors;
import java.util.*;

@RestController
@RequestMapping("/api/bookings")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class BookingController {

    private final BookingService bookingService;
    private final TicketPDFService ticketPdfService;

    public BookingController(BookingService bookingService, TicketPDFService ticketPdfService) {
        this.bookingService = bookingService;
        this.ticketPdfService = ticketPdfService;
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

    // Endpoint này sẽ cập nhật trạng thái và trả về danh sách booking đã cập nhật
    @PostMapping("/confirm-payment/{txnRef}")
    public ResponseEntity<?> confirmPaymentAndUpdateStatus(@PathVariable String txnRef) {
        try {
            // Cập nhật trạng thái thành PAID
            List<Booking> updatedBookings = bookingService.markPaidByTxn(txnRef, "VNPAY");
            // Chuyển đổi sang DTO để tránh lỗi tuần tự hóa JSON (circular reference)
            List<SoldTicketDTO> updatedBookingsDTO = updatedBookings.stream()
                    .map(SoldTicketDTO::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(updatedBookingsDTO);
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

    @GetMapping(value = "/{bookingId}/ticket", produces = "application/pdf")
    public ResponseEntity<byte[]> downloadTicket(@PathVariable Long bookingId) {
        var booking = bookingService.findById(bookingId) // bạn thêm method này trong service
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        // chỉ cho tải nếu đã thanh toán
        if (booking.getStatus() != Booking.Status.PAID) {
            return ResponseEntity.status(403)
                    .body(("Vé chưa thanh toán - không thể xuất PDF").getBytes());
        }

        byte[] pdf = ticketPdfService.generate(booking);

        String filename = "ticket_" + bookingId + ".pdf";
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }

    // ==================== CUSTOMER ====================
    @PostMapping
    public ResponseEntity<?> createMultiByCustomer(@RequestBody BookingRequest req, Authentication authentication) {
        try {
            if (req.getSeatIds() == null || req.getSeatIds().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Chưa chọn ghế nào!"));
            }

            if (authentication == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Bạn cần đăng nhập trước khi đặt vé."));
            }

            String customerUsername = authentication.getName();
            String txnRef = String.valueOf(System.currentTimeMillis());

            // Gọi sang hàm bạn vừa thêm
            List<Booking> bookings = bookingService.createMultiBookingForCustomer(
                    req.getShowtimeId(),
                    req.getSeatIds(),
                    txnRef,
                    customerUsername);

            return ResponseEntity.ok(Map.of(
                    "message", "Đã tạo " + bookings.size() + " booking, chờ thanh toán!",
                    "txnRef", txnRef));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // Endpoint mới để hủy các booking PENDING nếu thanh toán thất bại
    @PostMapping("/cancel-by-txn/{txnRef}")
    public ResponseEntity<?> cancelPendingBookingsByTxn(@PathVariable String txnRef) {
        try {
            bookingService.cancelPendingBookingsByTxn(txnRef);
            return ResponseEntity.ok(Map.of("message", "Đã hủy các booking đang chờ thanh toán."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Lỗi khi hủy booking: " + e.getMessage()));
        }
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<?> getBookingDetail(@PathVariable Long bookingId) {
        try {
            Booking booking = bookingService.findById(bookingId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy vé"));

            // Trả về DTO để tránh vòng lặp JSON và chỉ gửi dữ liệu cần thiết
            SoldTicketDTO dto = new SoldTicketDTO(booking);
            return ResponseEntity.ok(dto);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/txn/{txnRef}")
    public ResponseEntity<List<SoldTicketDTO>> getBookingsByTxnRef(@PathVariable String txnRef) {
        List<Booking> bookings = bookingService.findByTxnRef(txnRef);
        if (bookings.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<SoldTicketDTO> dtoList = bookings.stream()
                .map(SoldTicketDTO::new)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtoList);
    }

    @GetMapping("/group-ticket/{txnRef}")
    public ResponseEntity<byte[]> downloadGroupTicket(@PathVariable String txnRef) {
        List<Booking> bookings = bookingService.findByTxnRef(txnRef);
        if (bookings.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        byte[] pdfData = ticketPdfService.generateGroupPDF(bookings);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=tickets_" + txnRef + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfData);
    }

    @PostMapping("/mark-printed/{txnRef}")
    public ResponseEntity<?> markTicketsAsPrinted(@PathVariable String txnRef) {
        List<Booking> bookings = bookingService.findByTxnRef(txnRef);
        if (bookings.isEmpty()) {
            return ResponseEntity.badRequest().body("Không tìm thấy vé với mã giao dịch này!");
        }

        // Nếu tất cả đều đã in rồi → không cho in lại
        boolean alreadyPrinted = bookings.stream().allMatch(Booking::isPrinted);
        if (alreadyPrinted) {
            return ResponseEntity.status(409).body("Các vé trong mã này đã được in trước đó!");
        }

        bookings.forEach(b -> b.setPrinted(true));
        bookingService.saveAll(bookings);

        System.out.println("Đánh dấu đã in vé cho txnRef: " + txnRef);
        return ResponseEntity.ok("Đã đánh dấu vé đã in cho mã giao dịch: " + txnRef);
    }
}
