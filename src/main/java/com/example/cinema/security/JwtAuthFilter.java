package com.example.cinema.security;

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
import java.util.Arrays;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    private static final List<String> PUBLIC_URLS = Arrays.asList(
            "/api/auth/",
            "/api/movies",
            "/api/showtimes");

    public JwtAuthFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // ✅ Cho phép tất cả request tới các đường dẫn PUBLIC
        if (path.startsWith("/api/auth/"))
            return true;

        // ✅ Cho phép GET tới các API public (movie, showtime, seat)
        if ("GET".equals(method) && (path.startsWith("/api/movies")
                || path.startsWith("/api/showtimes")
                || path.startsWith("/api/seats")
                || path.startsWith("/api/bookings/showtime"))) {
            return true;
        }

        return false;
    }

    // @Override
    // protected void doFilterInternal(HttpServletRequest request,
    // HttpServletResponse response,
    // FilterChain filterChain)
    // throws ServletException, IOException {

    // String header = request.getHeader("Authorization");
    // if (header != null && header.startsWith("Bearer ")) {
    // String token = header.substring(7);
    // if (jwtUtil.validateToken(token)) {
    // String username = jwtUtil.extractUsername(token);
    // String role = jwtUtil.extractRole(token); // hoặc lấy từ DB

    // List<GrantedAuthority> authorities = List.of(new
    // SimpleGrantedAuthority("ROLE_" + role));

    // UsernamePasswordAuthenticationToken authToken = new
    // UsernamePasswordAuthenticationToken(username, null,
    // authorities);

    // authToken.setDetails(new
    // WebAuthenticationDetailsSource().buildDetails(request));
    // SecurityContextHolder.getContext().setAuthentication(authToken);
    // }
    // }
    // filterChain.doFilter(request, response);
    // }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Nếu là public URL thì cho qua luôn
        if (shouldNotFilter(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Unauthorized - token missing or invalid\"}");
            return;
        }

        String token = header.substring(7);
        String username = null;

        try {
            username = jwtUtil.extractUsername(token);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Invalid token\"}");
            return;
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (jwtUtil.validateToken(token, userDetails)) {
                String role = jwtUtil.extractRole(token);
                List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        authorities);

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                filterChain.doFilter(request, response);
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"Invalid JWT token\"}");
            }
        }
    }

}
