package com.example.cinema.config;

import com.example.cinema.security.JwtAuthFilter;
import com.example.cinema.security.JwtAuthenticationEntryPoint;
import com.example.cinema.security.JwtAccessDeniedHandler;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

@Configuration
public class SecurityConfig {
        private final JwtAuthFilter jwtAuthFilter;
        private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
        private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

        public SecurityConfig(
                        JwtAuthFilter jwtAuthFilter,
                        JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                        JwtAccessDeniedHandler jwtAccessDeniedHandler) {

                this.jwtAuthFilter = jwtAuthFilter;
                this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
                this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .cors(cors -> cors.configurationSource(request -> {
                                        var corsConfig = new CorsConfiguration();
                                        corsConfig.setAllowedOriginPatterns(List.of("http://localhost:5173")); // frontend
                                        corsConfig.setAllowedMethods(
                                                        List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                                        corsConfig.setAllowedHeaders(List.of("*"));
                                        corsConfig.setAllowCredentials(true);
                                        return corsConfig;
                                }))
                                .csrf(csrf -> csrf.disable())
                                .authorizeHttpRequests(auth -> auth

                                                // 1️PUBLIC – login & VNPay callback
                                                .requestMatchers("/api/auth/**",
                                                                "/api/payments/vnpay-return")
                                                .permitAll()

                                                // Cho phép cập nhật trạng thái thanh toán (VNPay / frontend callback)
                                                .requestMatchers(HttpMethod.POST, "/api/bookings/pay-by-txn/{txnRef}")
                                                .permitAll()

                                                // Public GET cho phim, suất chiếu, ghế
                                                .requestMatchers(HttpMethod.GET,
                                                                "/api/movies/**",
                                                                "/api/showtimes/**",
                                                                "/api/seats/**",
                                                                "/api/snacks/**")
                                                .permitAll()

                                                // Cho phép người dùng đã đăng nhập gửi review phim
                                                .requestMatchers(HttpMethod.POST, "/api/movies/*/reviews")
                                                .hasRole("CUSTOMER")

                                                // 2️STAFF zone (chỉ STAFF mới được truy cập) - phải đặt sau rule cụ thể
                                                // ở trên
                                                .requestMatchers("/api/staff/**").hasRole("STAFF")

                                                // ADMIN hoặc STAFF có thể xem booking theo suất chiếu
                                                .requestMatchers(HttpMethod.GET, "/api/bookings/showtime/{showtimeId}")
                                                .hasAnyRole("ADMIN", "STAFF")

                                                // ROOMS – STAFF & ADMIN xem, ADMIN quản lý
                                                .requestMatchers(HttpMethod.GET, "/api/rooms/**")
                                                .hasAnyRole("ADMIN", "STAFF")
                                                .requestMatchers("/api/rooms/**").hasRole("ADMIN")

                                                // ADMIN ONLY – chỉnh sửa dữ liệu
                                                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                                                .requestMatchers("/api/movies/**").hasRole("ADMIN")
                                                .requestMatchers("/api/seats/**").hasRole("ADMIN")
                                                .requestMatchers("/api/showtimes/**").hasRole("ADMIN")

                                                // BOOKING endpoints
                                                .requestMatchers(HttpMethod.POST, "/api/bookings")
                                                .hasAnyRole("CUSTOMER", "STAFF", "ADMIN")

                                                .requestMatchers(HttpMethod.GET, "/api/bookings/me")
                                                .hasRole("CUSTOMER")

                                                .requestMatchers(HttpMethod.POST, "/api/bookings/{bookingId}/cancel")
                                                .hasAnyRole("CUSTOMER", "ADMIN")

                                                .requestMatchers(HttpMethod.GET, "/api/bookings/{bookingId}/ticket")
                                                .hasAnyRole("STAFF", "ADMIN")

                                                // PAYMENT endpoints
                                                .requestMatchers("/api/payments/**")
                                                .hasAnyRole("CUSTOMER", "STAFF", "ADMIN")

                                                // Mặc định – yêu cầu đăng nhập
                                                .anyRequest().authenticated())

                                .exceptionHandling(e -> e
                                                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                                                .accessDeniedHandler(jwtAccessDeniedHandler))

                                .sessionManagement(s -> s
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

                http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
                return http.build();
        }

        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
                        throws Exception {
                return config.getAuthenticationManager();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }
}
