package com.example.cinema.security;

import com.example.cinema.domain.Customer;
import com.example.cinema.domain.User;
import com.example.cinema.domain.Customer.Gender;
import com.example.cinema.repository.CustomerRepository;
import com.example.cinema.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {

        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        Map<String, Object> attributes = token.getPrincipal().getAttributes();

        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");

        // 1. Tạo user nếu chưa có
        User user = userRepository.findByUsername(email);
        if (user == null) {
            user = new User();
            user.setUsername(email);
            user.setPassword("GOOGLE_USER");
            user.setFullName(name);
            user.setRole(User.Role.CUSTOMER);
            user = userRepository.save(user);
        }

        // 2. Tạo customer nếu chưa có (tránh lambda)
        if (customerRepository.findByUserUserId(user.getUserId()).isEmpty()) {
            Customer c = new Customer();
            c.setUser(user);
            c.setEmail(email);
            c.setGender(Gender.MALE);
            c.setLoyaltyPoints(0);
            customerRepository.save(c);
        }

        // 3. Sinh JWT token và redirect
        String jwt = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name(), user.getFullName());
        
        // Lưu token vào database - vô hiệu hóa session cũ (nếu có)
        user.setCurrentSessionToken(jwt);
        userRepository.save(user);
        
        String redirectUrl = "http://localhost:5173/login-success?token=" + jwt;
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
