package com.example.cinema.controller;

import com.example.cinema.domain.Ticket;
import com.example.cinema.service.TicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
@CrossOrigin(origins = "*") // Cho phép VueJS truy cập từ localhost
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    // Bán vé mới (nhân viên thực hiện)
    @PostMapping("/sell")
    public ResponseEntity<?> sellTicket(
            @RequestParam String username, // username của nhân viên bán
            @RequestParam Long showtimeId, // id suất chiếu
            @RequestParam String seatNumber, // số ghế
            @RequestParam(required = false) Double price // giá vé (tùy chọn)
    ) {
        try {
            Ticket ticket = ticketService.createTicket(username, showtimeId, seatNumber, price);
            return ResponseEntity.ok(ticket);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Lấy danh sách vé theo username nhân viên
    @GetMapping("/staff/{username}")
    public ResponseEntity<List<Ticket>> getTicketsByStaffUsername(@PathVariable String username) {
        List<Ticket> tickets = ticketService.findByStaffUsername(username);
        return ResponseEntity.ok(tickets);
    }

    // Lấy danh sách vé theo ID nhân viên
    @GetMapping("/staff/id/{staffId}")
    public ResponseEntity<List<Ticket>> getTicketsByStaffId(@PathVariable Long staffId) {
        List<Ticket> tickets = ticketService.findByStaffId(staffId);
        return ResponseEntity.ok(tickets);
    }

    // Đếm số vé của 1 suất chiếu
    @GetMapping("/showtime/{showtimeId}/count")
    public ResponseEntity<Long> countTicketsByShowtime(@PathVariable Long showtimeId) {
        Long count = ticketService.countByShowtime(showtimeId);
        return ResponseEntity.ok(count);
    }

    // Thống kê doanh thu theo nhân viên
    @GetMapping("/revenue/staff")
    public ResponseEntity<List<Map<String, Object>>> getRevenueByStaff() {
        return ResponseEntity.ok(ticketService.getRevenueByStaff());
    }

    // Thống kê doanh thu theo tháng
    @GetMapping("/revenue/monthly")
    public ResponseEntity<List<Map<String, Object>>> getMonthlyRevenue() {
        return ResponseEntity.ok(ticketService.getMonthlyRevenue());
    }

    // Xóa vé (chỉ admin)
    @DeleteMapping("/{ticketId}")
    public ResponseEntity<?> deleteTicket(@PathVariable Long ticketId) {
        ticketService.deleteTicket(ticketId);
        return ResponseEntity.ok(Map.of("message", "Ticket deleted successfully"));
    }
}
