package com.example.cinema.controller;

import com.example.cinema.service.BookingService;
import com.example.cinema.repository.*;
import com.example.cinema.domain.Customer;
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

    @GetMapping("/revenue/time-slots")
    public ResponseEntity<?> getRevenueByTimeSlot(@RequestParam(required = false) Integer year) {
        int targetYear = year != null ? year : java.time.LocalDate.now().getYear();
        return ResponseEntity.ok(bookingService.getRevenueByTimeSlot(targetYear));
    }

    @GetMapping("/staffs")
    public ResponseEntity<?> getAllStaffs() {
        List<Map<String, Object>> staffList = staffRepository.findAll().stream()
                .map(s -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("staffId", s.getStaffId());
                    m.put("userId", s.getUser().getUserId());
                    m.put("username", s.getUser().getEmail());
                    m.put("fullName", s.getUser().getFullName());
                    m.put("role", s.getUser().getRole());
                    m.put("email", s.getEmail());
                    m.put("phone", s.getPhone());
                    m.put("userEmail", s.getUser().getEmail());
                    m.put("userPhone", s.getUser().getPhone());
                    m.put("cccd", s.getCccd());
                    m.put("position", s.getPosition());
                    m.put("salary", s.getSalary());
                    m.put("gender", s.getGender());
                    m.put("staffStatus", s.getStatus());
                    m.put("hireDate", s.getHireDate());
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getRole() == User.Role.ADMIN) {
            throw new IllegalArgumentException("Không thể xóa tài khoản admin");
        }

        boolean hasCustomerBookings = bookingRepo.existsByCustomer_User_UserId(userId);
        boolean hasStaffBookings = bookingRepo.existsBySoldByStaff_User_UserId(userId);

        if (hasCustomerBookings || hasStaffBookings) {
            user.setIsActive(false);
            userRepository.save(user);

            if (hasStaffBookings) {
                staffRepository.findByUser_UserId(userId).ifPresent(staff -> {
                    staff.setStatus(com.example.cinema.domain.Staff.Status.INACTIVE);
                    staffRepository.save(staff);
                });
            }

            String reason = hasCustomerBookings
                    ? "Tài khoản có lịch sử đặt vé"
                    : "Tài khoản có lịch sử bán vé";

            Map<String, Object> payload = new HashMap<>();
            payload.put("message", "Không thể xóa tài khoản vì " + reason + ". Đã chuyển sang trạng thái khóa.");
            payload.put("success", true);
            payload.put("deleted", false);
            payload.put("locked", true);
            return ResponseEntity.ok(payload);
        }

        userRepository.delete(user);
        return ResponseEntity.ok(Map.of(
                "message", "Tài khoản đã bị xóa",
                "success", true,
                "deleted", true,
                "locked", false));
    }

    // Lấy danh sách tất cả người dùng (Customers)
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        List<Map<String, Object>> userList = userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.Role.CUSTOMER)
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("userId", u.getUserId());
                    m.put("username", u.getEmail());
                    m.put("email", u.getEmail());
                    m.put("phone", u.getPhone());
                    m.put("fullName", u.getFullName());
                    m.put("role", u.getRole());
                    m.put("isActive", u.getIsActive());
                    Customer customer = u.getCustomer();
                    if (customer != null) {
                        m.put("customerId", customer.getCustomerId());
                        m.put("address", customer.getAddress());
                        m.put("gender", customer.getGender());
                        m.put("loyaltyPoints", customer.getLoyaltyPoints());
                    }
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(userList);
    }

}
