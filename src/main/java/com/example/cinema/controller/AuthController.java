package com.example.cinema.controller;

import com.example.cinema.domain.Customer;
import com.example.cinema.domain.Staff;
import com.example.cinema.domain.User;
import com.example.cinema.dto.LoginRequest;
import com.example.cinema.dto.LoginResponse;
import com.example.cinema.dto.RegisterRequest;
import com.example.cinema.repository.CustomerRepository;
import com.example.cinema.repository.StaffRepository;
import com.example.cinema.repository.UserRepository;
import com.example.cinema.security.JwtUtil;

import jakarta.transaction.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin("*")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private CustomerRepository customerRepository;
    private StaffRepository staffRepository;

    public AuthController(JwtUtil jwtUtil, UserRepository userRepository, PasswordEncoder passwordEncoder,
            CustomerRepository customerRepository, StaffRepository staffRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.customerRepository = customerRepository;
        this.staffRepository = staffRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        User user = userRepository.findByUsername(req.getUsername());
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(new LoginResponse(null, "Bad credentials", null, null));
        }
        if (!user.getIsActive()) {
            return ResponseEntity.badRequest().body(new LoginResponse(null, "Account is locked", null, null));
        }
        
        // Tạo token mới
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name(), user.getFullName());
        
        // Lưu token vào database - điều này sẽ vô hiệu hóa session cũ (nếu có)
        user.setCurrentSessionToken(token);
        userRepository.save(user);
        
        return ResponseEntity.ok(new LoginResponse(token, "OK", user.getRole().name(), user.getUserId()));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        try {
            if (req.getUsername() == null || req.getUsername().trim().isEmpty() ||
                    req.getPassword() == null || req.getPassword().trim().isEmpty() ||
                    req.getFullName() == null || req.getFullName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Thiếu thông tin bắt buộc!"));
            }

            if (userRepository.findByUsername(req.getUsername()) != null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Tên đăng nhập đã tồn tại!"));
            }

            if (req.getEmail() != null && customerRepository.existsByEmail(req.getEmail())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email đã được sử dụng!"));
            }

            // Tạo User (chung cho mọi loại tài khoản)
            User user = new User();
            user.setUsername(req.getUsername());
            user.setPassword(passwordEncoder.encode(req.getPassword()));
            user.setFullName(req.getFullName());
            user.setRole(User.Role.CUSTOMER);
            userRepository.save(user);

            // Tạo Customer riêng
            Customer customer = new Customer();
            customer.setUser(user);
            customer.setPhone(req.getPhone());
            customer.setEmail(req.getEmail());
            customer.setAddress(req.getAddress());
            if (req.getGender() != null) {
                try {
                    customer.setGender(Customer.Gender.valueOf(req.getGender().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    customer.setGender(Customer.Gender.MALE);
                }
            }
            customerRepository.save(customer);

            return ResponseEntity.ok(Map.of(
                    "message", "Đăng ký thành công!",
                    "username", user.getUsername(),
                    "role", user.getRole().name()));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader("Authorization") String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body("Missing token");
        }

        String token = header.substring(7);
        String username = jwtUtil.extractUsername(token);
        User user = userRepository.findByUsername(username);

        if (user == null) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        if (!user.getIsActive()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Account is locked"));
        }

        // Trả JSON đơn giản, không phụ thuộc entity
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userId", user.getUserId());
        userInfo.put("username", user.getUsername());
        userInfo.put("fullName", user.getFullName());
        userInfo.put("role", user.getRole().name());
        userInfo.put("isActive", user.getIsActive());

        return ResponseEntity.ok(userInfo);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing token"));
        }

        String token = header.substring(7);
        String username = jwtUtil.extractUsername(token);
        User user = userRepository.findByUsername(username);

        if (user != null) {
            // Xóa currentSessionToken để vô hiệu hóa session
            user.setCurrentSessionToken(null);
            userRepository.save(user);
        }

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/register-admin")
    public ResponseEntity<?> registerAdmin(@RequestBody RegisterRequest req) {
        if (req.getUsername() == null || req.getUsername().trim().isEmpty() ||
                req.getPassword() == null || req.getPassword().trim().isEmpty() ||
                req.getFullName() == null || req.getFullName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Username, password and full name cannot be empty!");
        }

        if (userRepository.findByUsername(req.getUsername()) != null) {
            return ResponseEntity.badRequest().body("Username already exists!");
        }

        User newUser = new User();
        newUser.setUsername(req.getUsername());
        newUser.setPassword(passwordEncoder.encode(req.getPassword()));
        newUser.setFullName(req.getFullName());
        newUser.setRole(User.Role.ADMIN);

        userRepository.save(newUser);
        return ResponseEntity.ok("Admin created!");
    }

    @GetMapping("/profile/{userId}")
    public ResponseEntity<?> getProfile(@PathVariable Long userId) {
        try {
            System.out.println("DEBUG: Getting profile for userId: " + userId);
            
            // Lấy user
            Optional<User> userOpt = userRepository.findById(userId);
            if (!userOpt.isPresent()) {
                System.out.println("DEBUG: User not found with ID: " + userId);
                return ResponseEntity.status(404).body(Map.of("error", "Không tìm thấy người dùng với ID: " + userId));
            }
            
            User user = userOpt.get();
            System.out.println("DEBUG: Found user: " + user.getUsername() + ", role: " + user.getRole());

            // Tìm customer tương ứng
            Optional<Customer> customerOpt = customerRepository.findByUserUserId(userId);
            if (customerOpt.isPresent()) {
                Customer customer = customerOpt.get();
                System.out.println("DEBUG: Found customer with loyaltyPoints: " + customer.getLoyaltyPoints());
                
                // Tạo response map để tránh circular reference
                Map<String, Object> customerProfile = new HashMap<>();
                customerProfile.put("customerId", customer.getCustomerId());
                customerProfile.put("phone", customer.getPhone());
                customerProfile.put("email", customer.getEmail());
                customerProfile.put("address", customer.getAddress());
                customerProfile.put("gender", customer.getGender());
                customerProfile.put("loyaltyPoints", customer.getLoyaltyPoints());
                
                // User info
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("userId", user.getUserId());
                userInfo.put("username", user.getUsername());
                userInfo.put("fullName", user.getFullName());
                userInfo.put("role", user.getRole());
                userInfo.put("isActive", user.getIsActive());
                
                customerProfile.put("user", userInfo);
                
                return ResponseEntity.ok(customerProfile);
            }

            // Nếu không có customer (vd: ADMIN, STAFF)
            System.out.println("DEBUG: No customer found, returning basic user info");
            Map<String, Object> userProfile = new HashMap<>();
            userProfile.put("userId", user.getUserId());
            userProfile.put("loyaltyPoints", 0); // Default cho admin/staff
            
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("userId", user.getUserId());
            userInfo.put("username", user.getUsername());
            userInfo.put("fullName", user.getFullName());
            userInfo.put("role", user.getRole());
            userInfo.put("isActive", user.getIsActive());
            
            userProfile.put("user", userInfo);
            
            return ResponseEntity.ok(userProfile);
        } catch (Exception e) {
            System.err.println("ERROR in getProfile: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Lỗi khi tải profile: " + e.getMessage()));
        }
    }

    @Transactional
    @PutMapping("/profile/{userId}")
    public ResponseEntity<?> updateFullProfile(@PathVariable Long userId, @RequestBody Customer req) {
        Customer customer = customerRepository.findByUserUserId(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khách hàng"));

        // Cập nhật các thông tin cá nhân
        customer.setEmail(req.getEmail());
        customer.setPhone(req.getPhone());
        customer.setAddress(req.getAddress());
        customer.setGender(req.getGender());

        // Cập nhật tên trong bảng user (nếu có)
        User user = customer.getUser();
        if (req.getUser() != null && req.getUser().getFullName() != null) {
            user.setFullName(req.getUser().getFullName());
            userRepository.save(user);
        }

        customerRepository.save(customer);
        return ResponseEntity.ok("Cập nhật hồ sơ thành công!");
    }

    @PostMapping("/register-staff")
    public ResponseEntity<?> registerStaff(@RequestBody RegisterRequest req) {
        // Kiểm tra username & CCCD trùng
        if (userRepository.findByUsername(req.getUsername()) != null) {
            return ResponseEntity.badRequest().body("Tên đăng nhập đã tồn tại!");
        }
        if (staffRepository.existsByCccd(req.getCccd())) {
            return ResponseEntity.badRequest().body("CCCD đã tồn tại!");
        }

        // Tạo User mới
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setFullName(req.getFullName());
        user.setRole(User.Role.STAFF);
        userRepository.save(user);

        // Tạo Staff tương ứng
        Staff staff = new Staff();
        staff.setUser(user);
        staff.setCccd(req.getCccd());
        staff.setPhone(req.getPhone());
        staff.setEmail(req.getEmail());
        staff.setPosition(req.getPosition());
        staff.setSalary(req.getSalary());
        staff.setHireDate(new java.sql.Date(System.currentTimeMillis()));
        try {
            staff.setGender(Staff.Gender.valueOf(req.getGender().toUpperCase()));
        } catch (Exception e) {
            staff.setGender(Staff.Gender.MALE); // mặc định nếu bị lỗi
        }
        staff.setStatus(Staff.Status.ACTIVE);
        staffRepository.save(staff);

        return ResponseEntity.ok("Đã thêm nhân viên mới thành công!");
    }
}
