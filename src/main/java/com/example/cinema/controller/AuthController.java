package com.example.cinema.controller;

import com.example.cinema.domain.Customer;
import com.example.cinema.domain.Staff;
import com.example.cinema.domain.User;
import com.example.cinema.dto.LoginRequestDTO;
import com.example.cinema.dto.LoginResponseDTO;
import com.example.cinema.dto.RefreshTokenRequestDTO;
import com.example.cinema.dto.RegisterRequestDTO;
import com.example.cinema.dto.VerifyOtpRequestDTO;
import com.example.cinema.repository.CustomerRepository;
import com.example.cinema.repository.BookingRepository;
import com.example.cinema.repository.SnackOrderRepository;
import com.example.cinema.repository.StaffRepository;
import com.example.cinema.repository.UserRepository;
import com.example.cinema.security.JwtUtil;
import com.example.cinema.service.RegistrationOtpService;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin("*")
public class AuthController {

    private static final int PROFILE_SPENDING_WINDOW_DAYS = 365;

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegistrationOtpService registrationOtpService;
    private final CustomerRepository customerRepository;
    private final BookingRepository bookingRepository;
    private final SnackOrderRepository snackOrderRepository;
    private final StaffRepository staffRepository;

    public AuthController(JwtUtil jwtUtil, UserRepository userRepository, PasswordEncoder passwordEncoder,
                          RegistrationOtpService registrationOtpService,
                          CustomerRepository customerRepository, BookingRepository bookingRepository,
                          SnackOrderRepository snackOrderRepository, StaffRepository staffRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.registrationOtpService = registrationOtpService;
        this.customerRepository = customerRepository;
        this.bookingRepository = bookingRepository;
        this.snackOrderRepository = snackOrderRepository;
        this.staffRepository = staffRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO req) {
        String identifier = req.getLoginIdentifier();
        if (identifier == null || identifier.isBlank()) {
            return ResponseEntity.badRequest().body(emptyLoginResponse("Missing identifier"));
        }

        User user = userRepository.findByEmailOrPhone(identifier, identifier);
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(emptyLoginResponse("Bad credentials"));
        }
        if (!user.getIsActive()) {
            return ResponseEntity.badRequest().body(emptyLoginResponse("Account is locked"));
        }

        return ResponseEntity.ok(issueSession(user));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshTokenRequestDTO req) {
        if (req == null || req.getRefreshToken() == null || req.getRefreshToken().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing refresh token"));
        }

        String refreshToken = req.getRefreshToken().trim();

        try {
            if (!jwtUtil.isRefreshToken(refreshToken)) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid refresh token"));
            }

            String identifier = jwtUtil.extractIdentifier(refreshToken);
            User user = userRepository.findByEmail(identifier);
            if (user == null || !user.getIsActive()) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid refresh token"));
            }

            if (!refreshToken.equals(user.getCurrentRefreshToken())) {
                return ResponseEntity.status(401).body(Map.of("error", "Refresh token expired", "code", "CONCURRENT_LOGIN"));
            }

            String currentSessionId = user.getCurrentSessionId();
            String tokenSessionId = jwtUtil.extractSessionId(refreshToken);
            if (currentSessionId == null || tokenSessionId == null || !currentSessionId.equals(tokenSessionId)) {
                return ResponseEntity.status(401).body(Map.of("error", "Refresh token expired", "code", "CONCURRENT_LOGIN"));
            }

            if (!jwtUtil.validateToken(refreshToken, user.getEmail(), "refresh")) {
                return ResponseEntity.status(401).body(Map.of("error", "Refresh token expired"));
            }

            return ResponseEntity.ok(refreshSession(user));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid refresh token"));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequestDTO req) {
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
    public ResponseEntity<?> sendRegisterOtp(@RequestBody RegisterRequestDTO req) {
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
    public ResponseEntity<?> verifyRegisterOtp(@RequestBody VerifyOtpRequestDTO req) {
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
            user.setCurrentSessionId(null);
            user.setCurrentRefreshToken(null);
            userRepository.save(user);
        }

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/register-admin")
    public ResponseEntity<?> registerAdmin(@RequestBody RegisterRequestDTO req) {
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

            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                System.out.println("DEBUG: User not found with ID: " + userId);
                return ResponseEntity.status(404).body(Map.of("error", "Không tìm thấy người dùng với ID: " + userId));
            }

            User user = userOpt.get();
            System.out.println("DEBUG: Found user: " + user.getEmail() + ", role: " + user.getRole());

            Optional<Customer> customerOpt = customerRepository.findByUserUserId(userId);
            if (customerOpt.isPresent()) {
                Customer customer = customerOpt.get();
                System.out.println("DEBUG: Found customer with loyaltyPoints: " + customer.getLoyaltyPoints());

                Map<String, Object> customerProfile = new HashMap<>();
                customerProfile.put("customerId", customer.getCustomerId());
                customerProfile.put("phone", user.getPhone());
                customerProfile.put("email", user.getEmail());
                customerProfile.put("address", customer.getAddress());
                customerProfile.put("gender", customer.getGender());
                customerProfile.put("loyaltyPoints", customer.getLoyaltyPoints());
                customerProfile.put("totalSpent", getTotalSpent(userId));
                customerProfile.put("spendingWindowDays", PROFILE_SPENDING_WINDOW_DAYS);
                customerProfile.put("spendingYear", LocalDate.now().getYear());
                customerProfile.put("lifetimeTotalSpent", getLifetimeTotalSpent(userId));

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

            System.out.println("DEBUG: No customer found, returning basic user info");
            Map<String, Object> userProfile = new HashMap<>();
            userProfile.put("userId", user.getUserId());
            userProfile.put("loyaltyPoints", 0);
            userProfile.put("totalSpent", 0.0);
            userProfile.put("spendingWindowDays", PROFILE_SPENDING_WINDOW_DAYS);
            userProfile.put("spendingYear", LocalDate.now().getYear());
            userProfile.put("lifetimeTotalSpent", 0.0);

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

    private double getTotalSpent(Long userId) {
        LocalDateTime from = getSpendingWindowStart(PROFILE_SPENDING_WINDOW_DAYS);
        Double bookingSpent = bookingRepository.sumPaidAmountByCustomerUserIdSince(userId, from);
        Double snackSpent = snackOrderRepository.sumPaidStandaloneAmountByCustomerUserIdSince(userId, from);
        return defaultAmount(bookingSpent) + defaultAmount(snackSpent);
    }

    private LocalDateTime getSpendingWindowStart(int windowDays) {
        LocalDate today = LocalDate.now();
        int years = Math.max(1, Math.round(windowDays / 365.0f));
        int startYear = today.getYear() - years + 1;
        return LocalDate.of(startYear, 1, 1).atStartOfDay();
    }

    private double getLifetimeTotalSpent(Long userId) {
        Double bookingSpent = bookingRepository.sumPaidAmountByCustomerUserId(userId);
        Double snackSpent = snackOrderRepository.sumPaidStandaloneAmountByCustomerUserId(userId);
        return defaultAmount(bookingSpent) + defaultAmount(snackSpent);
    }

    private double defaultAmount(Double value) {
        return value == null ? 0.0 : value;
    }

    @Transactional
    @PutMapping("/profile/{userId}")
    public ResponseEntity<?> updateFullProfile(@PathVariable Long userId, @RequestBody Customer req) {
        Customer customer = customerRepository.findByUserUserId(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khách hàng"));

        customer.setAddress(req.getAddress());
        customer.setGender(req.getGender());

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
    public ResponseEntity<?> registerStaff(@RequestBody RegisterRequestDTO req) {
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

        User user = new User();
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setFullName(req.getFullName());
        user.setRole(User.Role.STAFF);
        userRepository.save(user);

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
            staff.setGender(Staff.Gender.MALE);
        }
        staff.setStatus(Staff.Status.ACTIVE);
        staffRepository.save(staff);

        return ResponseEntity.ok("Đã thêm nhân viên mới thành công!");
    }

    private LoginResponseDTO issueSession(User user) {
        return issueSession(user, jwtUtil.generateSessionId());
    }

    private LoginResponseDTO refreshSession(User user) {
        String existingSessionId = user.getCurrentSessionId();
        if (existingSessionId == null || existingSessionId.isBlank()) {
            existingSessionId = jwtUtil.generateSessionId();
        }
        return issueSession(user, existingSessionId);
    }

    private LoginResponseDTO issueSession(User user, String sessionId) {
        String accessToken = jwtUtil.generateAccessToken(
                user.getEmail(),
                user.getRole().name(),
                user.getFullName(),
                sessionId);
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail(), sessionId);

        user.setCurrentSessionId(sessionId);
        user.setCurrentRefreshToken(refreshToken);
        userRepository.save(user);

        return LoginResponseDTO.builder()
                .token(accessToken)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .message("OK")
                .role(user.getRole().name())
                .userId(user.getUserId())
                .build();
    }

    private LoginResponseDTO emptyLoginResponse(String message) {
        return LoginResponseDTO.builder()
                .token(null)
                .accessToken(null)
                .refreshToken(null)
                .message(message)
                .role(null)
                .userId(null)
                .build();
    }
}
