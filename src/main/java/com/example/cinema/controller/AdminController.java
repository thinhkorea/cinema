package com.example.cinema.controller;

import com.example.cinema.service.BookingService;
import com.example.cinema.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final BookingService bookingService;
    private final MovieRepository movieRepo;
    private final RoomRepository roomRepo;
    private final ShowtimeRepository showtimeRepo;
    private final BookingRepository bookingRepo;

    public AdminController(
            BookingService bookingService,
            MovieRepository movieRepo,
            RoomRepository roomRepo,
            ShowtimeRepository showtimeRepo,
            BookingRepository bookingRepo) {
        this.bookingService = bookingService;
        this.movieRepo = movieRepo;
        this.roomRepo = roomRepo;
        this.showtimeRepo = showtimeRepo;
        this.bookingRepo = bookingRepo;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("movies", movieRepo.count());
        stats.put("rooms", roomRepo.count());
        stats.put("showtimes", showtimeRepo.count());
        stats.put("bookings", bookingRepo.count());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/bookings")
    public ResponseEntity<?> getAllBookings() {
        return ResponseEntity.ok(bookingService.findAllDTO());
    }

    @GetMapping("/revenue")
    public ResponseEntity<?> getMonthlyRevenue(@RequestParam(required = false) Integer year) {
        if (year != null) {
            return ResponseEntity.ok(bookingService.getMonthlyRevenueByYear(year));
        }
        return ResponseEntity.ok(bookingService.getMonthlyRevenue());
    }

    // Top phim theo doanh thu
    @GetMapping("/revenue/movies")
    public ResponseEntity<?> getRevenueByMovie() {
        return ResponseEntity.ok(bookingService.getRevenueByMovie());
    }

    // Top nhân viên theo doanh thu bán vé
    @GetMapping("/revenue/staffs")
    public ResponseEntity<?> getRevenueByStaff() {
        // Sử dụng BookingService để đảm bảo tính nhất quán dữ liệu
        return ResponseEntity.ok(bookingService.getRevenueByStaff());
    }
}
