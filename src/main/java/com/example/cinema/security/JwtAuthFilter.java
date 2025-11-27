package com.example.cinema.security;

import com.example.cinema.domain.User;
import com.example.cinema.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Cho phép ONLY login, register và register-admin bỏ qua filter
        if (path.equals("/api/auth/login") || 
            path.equals("/api/auth/register") || 
            path.equals("/api/auth/register-admin")) {
            return true;
        }
        
        // /api/auth/me và /api/auth/logout PHẢI qua filter để validate session

        // Cho phép callback từ VNPay (không chứa token)
        if (path.startsWith("/api/payments/vnpay-return")) {
            return true;
        }

        // Cho phép GET tới các API public (chỉ movies, showtimes, seats - KHÔNG BAO
        // GỒM bookings)
        if ("GET".equals(method) && (path.startsWith("/api/movies") ||
                path.startsWith("/api/showtimes") ||
                path.startsWith("/api/seats"))) {
            return true;
        }
        // TẤT CẢ requests khác (bao gồm /api/bookings/**) ĐỀU PHẢI QUA FILTER
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        // Không có token → 401
        if (header == null || !header.startsWith("Bearer ")) {
            System.out.println("No Authorization header or invalid format");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Unauthorized - token missing or invalid\"}");
            return;
        }

        String token = header.substring(7);
        String username = null;

        try {
            username = jwtUtil.extractUsername(token);
        } catch (Exception e) {
            System.out.println("Token parsing error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid token\"}");
            return;
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            System.out.println("JWT Filter - Username from token: " + username);
            System.out.println("JWT Filter - UserDetails loaded: " + userDetails.getUsername());

            if (jwtUtil.validateToken(token, userDetails)) {
                // Kiểm tra xem token có khớp với currentSessionToken trong database không
                User user = userRepository.findByUsername(username);
                if (user == null || !token.equals(user.getCurrentSessionToken())) {
                    System.out.println("Session không hợp lệ - có thể đã đăng nhập từ nơi khác");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"Session expired - logged in from another location\", \"code\": \"CONCURRENT_LOGIN\"}");
                    return;
                }
                
                // Extract role từ token
                String role = jwtUtil.extractRole(token);
                System.out.println("JWT Filter - Role from token: " + role);
                System.out.println("JWT Filter - Authority: ROLE_" + role);

                // Tạo authorities với ROLE_ prefix
                List<GrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + role));

                // Tạo authentication object
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        authorities);

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Set vào SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authToken);

                System.out.println("JWT Filter - Authentication set successfully");
                System.out.println("JWT Filter - Request path: " + request.getRequestURI());

                // Cho request đi tiếp
                filterChain.doFilter(request, response);
            } else {
                System.out.println("Token validation failed");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Invalid JWT token\"}");
            }
        } else {
            System.out.println("Username is null or authentication already exists");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Authentication failed\"}");
        }
    }
}