package com.example.cinema.controller;

import com.example.cinema.domain.Customer;
import com.example.cinema.domain.Staff;
import com.example.cinema.domain.User;
import com.example.cinema.dto.LoginRequest;
import com.example.cinema.dto.LoginResponse;
import com.example.cinema.dto.RegisterRequest;
import com.example.cinema.dto.VerifyOtpRequest;
import com.example.cinema.repository.CustomerRepository;
import com.example.cinema.repository.StaffRepository;
import com.example.cinema.repository.UserRepository;
import com.example.cinema.security.JwtUtil;
import com.example.cinema.service.RegistrationOtpService;

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
    private final RegistrationOtpService registrationOtpService;
    private CustomerRepository customerRepository;
    private StaffRepository staffRepository;

    public AuthController(JwtUtil jwtUtil, UserRepository userRepository, PasswordEncoder passwordEncoder,
            RegistrationOtpService registrationOtpService,
            CustomerRepository customerRepository, StaffRepository staffRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.registrationOtpService = registrationOtpService;
        this.customerRepository = customerRepository;
        this.staffRepository = staffRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        String identifier = req.getLoginIdentifier();
        if (identifier == null || identifier.isBlank()) {
            return ResponseEntity.badRequest().body(new LoginResponse(null, "Missing identifier", null, null));
        }

        User user = userRepository.findByEmailOrPhone(identifier, identifier);
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(new LoginResponse(null, "Bad credentials", null, null));
        }
        if (!user.getIsActive()) {
            return ResponseEntity.badRequest().body(new LoginResponse(null, "Account is locked", null, null));
        }
        
        // Tạo token mới
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name(), user.getFullName());
        
        // Lưu token vào database - điều này sẽ vô hiệu hóa session cũ (nếu có)
        user.setCurrentSessionToken(token);
        userRepository.save(user);
        
        return ResponseEntity.ok(new LoginResponse(token, "OK", user.getRole().name(), user.getUserId()));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        try {
            registrationOtpService.sendOtp(req);

            return ResponseEntity.ok(Map.of(
                    "message", "OTP đã được gửi về email. Vui lòng xác thực để hoàn tất đăng ký."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/register/send-otp")
    public ResponseEntity<?> sendRegisterOtp(@RequestBody RegisterRequest req) {
        try {
            registrationOtpService.sendOtp(req);
            return ResponseEntity.ok(Map.of("message", "OTP đã được gửi về email."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/register/verify-otp")
    public ResponseEntity<?> verifyRegisterOtp(@RequestBody VerifyOtpRequest req) {
        try {
            User user = registrationOtpService.verifyOtpAndRegister(req);
            return ResponseEntity.ok(Map.of(
                    "message", "Đăng ký thành công!",
                    "username", user.getEmail(),
                    "role", user.getRole().name()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
        String identifier = jwtUtil.extractIdentifier(token);
        User user = userRepository.findByEmail(identifier);

        if (user == null) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        if (!user.getIsActive()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Account is locked"));
        }

        // Trả JSON đơn giản, không phụ thuộc entity
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userId", user.getUserId());
        userInfo.put("username", user.getEmail());
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
        String identifier = jwtUtil.extractIdentifier(token);
        User user = userRepository.findByEmail(identifier);

        if (user != null) {
            // Xóa currentSessionToken để vô hiệu hóa session
            user.setCurrentSessionToken(null);
            userRepository.save(user);
        }

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/register-admin")
    public ResponseEntity<?> registerAdmin(@RequestBody RegisterRequest req) {
        if (req.getEmail() == null || req.getEmail().trim().isEmpty() ||
                req.getPassword() == null || req.getPassword().trim().isEmpty() ||
                req.getFullName() == null || req.getFullName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Email, password and full name cannot be empty!");
        }

        if (userRepository.existsByEmail(req.getEmail())) {
            return ResponseEntity.badRequest().body("Email already exists!");
        }

        User newUser = new User();
        newUser.setEmail(req.getEmail());
        newUser.setPhone(req.getPhone());
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
            System.out.println("DEBUG: Found user: " + user.getEmail() + ", role: " + user.getRole());

            // Tìm customer tương ứng
            Optional<Customer> customerOpt = customerRepository.findByUserUserId(userId);
            if (customerOpt.isPresent()) {
                Customer customer = customerOpt.get();
                System.out.println("DEBUG: Found customer with loyaltyPoints: " + customer.getLoyaltyPoints());
                
                // Tạo response map để tránh circular reference
                Map<String, Object> customerProfile = new HashMap<>();
                customerProfile.put("customerId", customer.getCustomerId());
                customerProfile.put("phone", user.getPhone());
                customerProfile.put("email", user.getEmail());
                customerProfile.put("address", customer.getAddress());
                customerProfile.put("gender", customer.getGender());
                customerProfile.put("loyaltyPoints", customer.getLoyaltyPoints());
                
                // User info
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("userId", user.getUserId());
                userInfo.put("username", user.getEmail());
                userInfo.put("email", user.getEmail());
                userInfo.put("phone", user.getPhone());
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
            userInfo.put("username", user.getEmail());
            userInfo.put("email", user.getEmail());
            userInfo.put("phone", user.getPhone());
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

        // Cập nhật thông tin customer-only
        customer.setAddress(req.getAddress());
        customer.setGender(req.getGender());

        // Cập nhật thông tin định danh trong bảng users
        User user = customer.getUser();
        if (req.getUser() != null && req.getUser().getFullName() != null) {
            user.setFullName(req.getUser().getFullName());
        }

        if (req.getUser() != null && req.getUser().getEmail() != null && !req.getUser().getEmail().isBlank()) {
            User existingByEmail = userRepository.findByEmail(req.getUser().getEmail());
            if (existingByEmail != null && !existingByEmail.getUserId().equals(userId)) {
                return ResponseEntity.badRequest().body("Email đã được sử dụng bởi tài khoản khác");
            }
            user.setEmail(req.getUser().getEmail().trim());
        }

        if (req.getUser() != null && req.getUser().getPhone() != null && !req.getUser().getPhone().isBlank()) {
            User existingByPhone = userRepository.findByPhone(req.getUser().getPhone());
            if (existingByPhone != null && !existingByPhone.getUserId().equals(userId)) {
                return ResponseEntity.badRequest().body("Số điện thoại đã được sử dụng bởi tài khoản khác");
            }
            user.setPhone(req.getUser().getPhone().trim());
        }

        userRepository.save(user);
        customerRepository.save(customer);
        return ResponseEntity.ok("Cập nhật hồ sơ thành công!");
    }

    @PostMapping("/register-staff")
    public ResponseEntity<?> registerStaff(@RequestBody RegisterRequest req) {
        // Kiểm tra email / phone / CCCD trùng
        if (req.getEmail() == null || req.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body("Email là bắt buộc!");
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            return ResponseEntity.badRequest().body("Email đã tồn tại!");
        }
        if (req.getPhone() != null && !req.getPhone().isBlank() && userRepository.existsByPhone(req.getPhone())) {
            return ResponseEntity.badRequest().body("Số điện thoại đã tồn tại!");
        }
        if (staffRepository.existsByCccd(req.getCccd())) {
            return ResponseEntity.badRequest().body("CCCD đã tồn tại!");
        }

        // Tạo User mới
        User user = new User();
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
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
