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

        if (path.equals("/api/auth/login") ||
                path.equals("/api/auth/refresh") ||
                path.equals("/api/auth/register") ||
                path.equals("/api/auth/register-admin") ||
                path.equals("/api/auth/register/send-otp") ||
                path.equals("/api/auth/register/verify-otp")) {
            return true;
        }

        if ("OPTIONS".equals(method)) {
            return true;
        }

        if (path.startsWith("/api/payments/vnpay-return")) {
            return true;
        }

        if (("GET".equals(method) || "HEAD".equals(method)) && (path.startsWith("/api/movies") ||
                path.startsWith("/api/showtimes") ||
                path.startsWith("/api/seats") ||
                (path.startsWith("/api/snacks") && !path.startsWith("/api/snacks/admin")))) {
            return true;
        }

        return false;
    }

    private boolean isOptionalAuthEndpoint(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && "/api/v1/bot/chat".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            if (isOptionalAuthEndpoint(request)) {
                filterChain.doFilter(request, response);
                return;
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Unauthorized - token missing or invalid\"}");
            return;
        }

        String token = header.substring(7);
        String identifier;

        try {
            identifier = jwtUtil.extractIdentifier(token);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid token\"}");
            return;
        }

        if (identifier == null || SecurityContextHolder.getContext().getAuthentication() != null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Authentication failed\"}");
            return;
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(identifier);
        if (!jwtUtil.validateToken(token, userDetails.getUsername(), "access")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid JWT token\"}");
            return;
        }

        User user = userRepository.findByEmail(userDetails.getUsername());
        String sessionId = jwtUtil.extractSessionId(token);
        if (user == null || sessionId == null || !sessionId.equals(user.getCurrentSessionId())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Session expired - logged in from another location\", \"code\": \"CONCURRENT_LOGIN\"}");
            return;
        }

        String role = jwtUtil.extractRole(token);
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                authorities);
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);

        filterChain.doFilter(request, response);
    }
}
