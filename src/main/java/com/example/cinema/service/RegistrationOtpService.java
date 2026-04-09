package com.example.cinema.service;

import com.example.cinema.domain.Customer;
import com.example.cinema.domain.RegistrationOtp;
import com.example.cinema.domain.User;
import com.example.cinema.dto.RegisterRequest;
import com.example.cinema.dto.VerifyOtpRequest;
import com.example.cinema.repository.CustomerRepository;
import com.example.cinema.repository.RegistrationOtpRepository;
import com.example.cinema.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class RegistrationOtpService {

    private final RegistrationOtpRepository registrationOtpRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationContext applicationContext;

    @Value("${otp.expiration-minutes:5}")
    private long otpExpirationMinutes;

    @Value("${otp.resend-cooldown-seconds:30}")
    private long otpResendCooldownSeconds;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Transactional
    public void sendOtp(RegisterRequest request) {
        validateRegisterRequest(request);

        String normalizedEmail = normalizeEmail(request.getEmail());
        String normalizedPhone = request.getPhone().trim();
        LocalDateTime now = LocalDateTime.now();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email đã tồn tại!");
        }

        if (userRepository.existsByPhone(normalizedPhone)) {
            throw new IllegalArgumentException("Số điện thoại đã tồn tại!");
        }

        RegistrationOtp latestOtp = registrationOtpRepository
                .findTopByEmailOrderByCreatedAtDesc(normalizedEmail)
                .orElse(null);

        if (latestOtp != null && latestOtp.getCreatedAt().plusSeconds(otpResendCooldownSeconds).isAfter(now)) {
            throw new IllegalArgumentException(
                    "Bạn vừa yêu cầu mã OTP. Vui lòng chờ " + otpResendCooldownSeconds + " giây để gửi lại.");
        }

        registrationOtpRepository.deleteByEmail(normalizedEmail);

        String otpCode = generateOtpCode();

        RegistrationOtp otp = new RegistrationOtp();
        otp.setEmail(normalizedEmail);
        otp.setOtpCode(otpCode);
        otp.setFullName(request.getFullName().trim());
        otp.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        otp.setPhone(normalizedPhone);
        otp.setAddress(request.getAddress() == null ? null : request.getAddress().trim());
        otp.setGender(request.getGender() == null ? null : request.getGender().trim().toUpperCase(Locale.ROOT));
        otp.setAttempts(0);
        otp.setCreatedAt(now);
        otp.setExpiresAt(now.plusMinutes(otpExpirationMinutes));

        RegistrationOtp savedOtp = registrationOtpRepository.save(otp);

        try {
            sendOtpEmail(normalizedEmail, request.getFullName(), otpCode);
        } catch (RuntimeException ex) {
            registrationOtpRepository.deleteById(savedOtp.getId());
            throw ex;
        }
    }

    @Transactional
    public User verifyOtpAndRegister(VerifyOtpRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank() ||
                request.getOtp() == null || request.getOtp().isBlank()) {
            throw new IllegalArgumentException("Email và OTP là bắt buộc!");
        }

        String normalizedEmail = normalizeEmail(request.getEmail());
        String normalizedOtp = request.getOtp().trim();

        RegistrationOtp otp = registrationOtpRepository
                .findTopByEmailOrderByCreatedAtDesc(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy OTP. Vui lòng đăng ký lại."));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            registrationOtpRepository.deleteByEmail(normalizedEmail);
            throw new IllegalArgumentException("OTP đã hết hạn. Vui lòng gửi lại OTP mới.");
        }

        if (otp.getAttempts() >= 5) {
            registrationOtpRepository.deleteByEmail(normalizedEmail);
            throw new IllegalArgumentException("Bạn đã nhập sai OTP quá nhiều lần. Vui lòng gửi lại OTP mới.");
        }

        if (!otp.getOtpCode().equals(normalizedOtp)) {
            otp.setAttempts(otp.getAttempts() + 1);
            registrationOtpRepository.save(otp);
            throw new IllegalArgumentException("OTP không chính xác!");
        }

        if (userRepository.existsByEmail(normalizedEmail)) {
            registrationOtpRepository.deleteByEmail(normalizedEmail);
            throw new IllegalArgumentException("Email đã tồn tại!");
        }

        if (userRepository.existsByPhone(otp.getPhone())) {
            registrationOtpRepository.deleteByEmail(normalizedEmail);
            throw new IllegalArgumentException("Số điện thoại đã tồn tại!");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPhone(otp.getPhone());
        user.setPassword(otp.getPasswordHash());
        user.setFullName(otp.getFullName());
        user.setRole(User.Role.CUSTOMER);
        userRepository.save(user);

        Customer customer = new Customer();
        customer.setUser(user);
        customer.setAddress(otp.getAddress());
        customer.setGender(parseGender(otp.getGender()));
        customerRepository.save(customer);

        registrationOtpRepository.deleteByEmail(normalizedEmail);

        return user;
    }

    private void validateRegisterRequest(RegisterRequest request) {
        if (request.getPassword() == null || request.getPassword().trim().isEmpty() ||
                request.getFullName() == null || request.getFullName().trim().isEmpty() ||
                request.getEmail() == null || request.getEmail().trim().isEmpty() ||
                request.getPhone() == null || request.getPhone().trim().isEmpty()) {
            throw new IllegalArgumentException("Thiếu thông tin bắt buộc!");
        }

        if (request.getPassword().trim().length() < 6) {
            throw new IllegalArgumentException("Mật khẩu phải có ít nhất 6 ký tự!");
        }
    }

    private Customer.Gender parseGender(String gender) {
        if (gender == null || gender.isBlank()) {
            return null;
        }
        try {
            return Customer.Gender.valueOf(gender.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String generateOtpCode() {
        int otpNumber = ThreadLocalRandom.current().nextInt(0, 1_000_000);
        return String.format("%06d", otpNumber);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void sendOtpEmail(String email, String fullName, String otpCode) {
        try {
            Class<?> simpleMailMessageClass = Class.forName("org.springframework.mail.SimpleMailMessage");
            Object message = simpleMailMessageClass.getDeclaredConstructor().newInstance();

            if (mailFrom != null && !mailFrom.isBlank()) {
                invokeMessageMethod(simpleMailMessageClass, message, "setFrom", new Class<?>[] { String.class },
                        mailFrom);
            }

            invokeMessageMethod(simpleMailMessageClass, message, "setTo", new Class<?>[] { String[].class },
                    (Object) new String[] { email });
            invokeMessageMethod(simpleMailMessageClass, message, "setSubject", new Class<?>[] { String.class },
                    "[Cinema] Mã OTP xác thực đăng ký");
            invokeMessageMethod(simpleMailMessageClass, message, "setText", new Class<?>[] { String.class },
                    "Xin chào " + fullName + ",\n\n"
                    + "Mã OTP đăng ký tài khoản của bạn là: " + otpCode + "\n"
                    + "Mã có hiệu lực trong " + otpExpirationMinutes + " phút.\n\n"
                    + "Nếu bạn không yêu cầu đăng ký, vui lòng bỏ qua email này.");

            Class<?> javaMailSenderClass = Class.forName("org.springframework.mail.javamail.JavaMailSender");
            Object mailSenderBean = applicationContext.getBean(javaMailSenderClass);
            Method sendMethod = javaMailSenderClass.getMethod("send", simpleMailMessageClass);
            sendMethod.invoke(mailSenderBean, message);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Thiếu thư viện gửi mail trong classpath IDE/runtime. Hãy reload Maven project.");
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Không thể gửi email OTP. Vui lòng kiểm tra cấu hình email server. Chi tiết: " + e.getMessage());
        }
    }

    private void invokeMessageMethod(Class<?> messageClass, Object message, String methodName,
            Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = messageClass.getMethod(methodName, parameterTypes);
        method.invoke(message, args);
    }
}
