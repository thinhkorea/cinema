package com.example.cinema.controller;

import com.example.cinema.service.BookingService;
import com.example.cinema.repository.*;
import com.example.cinema.domain.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final BookingService bookingService;
    private final MovieRepository movieRepo;
    private final RoomRepository roomRepo;
    private final ShowtimeRepository showtimeRepo;
    private final BookingRepository bookingRepo;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;

    public AdminController(
            BookingService bookingService,
            MovieRepository movieRepo,
            RoomRepository roomRepo,
            ShowtimeRepository showtimeRepo,
            BookingRepository bookingRepo,
            StaffRepository staffRepository,
            UserRepository userRepository) {
        this.bookingService = bookingService;
        this.movieRepo = movieRepo;
        this.roomRepo = roomRepo;
        this.showtimeRepo = showtimeRepo;
        this.bookingRepo = bookingRepo;
        this.staffRepository = staffRepository;
        this.userRepository = userRepository;
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

    @GetMapping("/staffs")
    public ResponseEntity<?> getAllStaffs() {
        List<Map<String, Object>> staffList = staffRepository.findAll().stream()
                .map(s -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("staffId", s.getStaffId());
                    m.put("userId", s.getUser().getUserId());
                    m.put("username", s.getUser().getUsername());
                    m.put("fullName", s.getUser().getFullName());
                    m.put("email", s.getEmail());
                    m.put("phone", s.getPhone());
                    m.put("createdAt", s.getHireDate());
                    m.put("isActive", s.getUser().getIsActive());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(staffList);
    }

    // Khóa tài khoản người dùng
    @PutMapping("/users/{userId}/lock")
    public ResponseEntity<?> lockUser(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setIsActive(false);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Tài khoản đã bị khóa", "success", true));
    }

    // Mở khóa tài khoản người dùng
    @PutMapping("/users/{userId}/unlock")
    public ResponseEntity<?> unlockUser(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setIsActive(true);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Tài khoản đã được mở khóa", "success", true));
    }

    // Xóa tài khoản người dùng
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        userRepository.deleteById(userId);
        return ResponseEntity.ok(Map.of("message", "Tài khoản đã bị xóa", "success", true));
    }

    // Lấy danh sách tất cả người dùng (Customers)
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        List<Map<String, Object>> userList = userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.Role.CUSTOMER)
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("userId", u.getUserId());
                    m.put("username", u.getUsername());
                    m.put("fullName", u.getFullName());
                    m.put("role", u.getRole());
                    m.put("isActive", u.getIsActive());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(userList);
    }

}
