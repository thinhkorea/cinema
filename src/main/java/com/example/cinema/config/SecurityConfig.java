package com.example.cinema.config;

import com.example.cinema.security.JwtAuthFilter;
import com.example.cinema.security.JwtAuthenticationEntryPoint;
import com.example.cinema.security.JwtAccessDeniedHandler;
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

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            JwtAccessDeniedHandler jwtAccessDeniedHandler) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // 1) PUBLIC – đặt trước để không bị rule tổng quát phía sau “nuốt”
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/movies/**",
                                "/api/showtimes/**",
                                "/api/seats/**",
                                "/api/bookings/showtime/*/seats-status")
                        .permitAll()

                        // 2) ROOMS – GET cho ADMIN/STAFF, còn lại ADMIN
                        .requestMatchers(HttpMethod.GET, "/api/rooms/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers("/api/rooms/**").hasRole("ADMIN")

                        // 3) ADMIN ONLY – tài nguyên quản trị & thao tác ghi
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/movies/**").hasRole("ADMIN")
                        .requestMatchers("/api/seats/**").hasRole("ADMIN")
                        .requestMatchers("/api/showtimes/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/bookings/showtime/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/bookings/*/pay").hasRole("ADMIN")

                        // 4) CUSTOMER endpoints
                        .requestMatchers(HttpMethod.GET, "/api/bookings/me").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/bookings").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/bookings/*/cancel").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/bookings/**").hasRole("ADMIN") // mới thêm sau này

                        // (quyền huỷ vé của customer sẽ kiểm tra thêm ở service theo owner)

                        // 5) Còn lại phải authenticated
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
