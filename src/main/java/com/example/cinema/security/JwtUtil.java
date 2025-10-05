package com.example.cinema.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {
    private final String SECRET = "supersecretkeysupersecretkeysupersecretkey"; // >= 32 ký tự
    private final long EXPIRATION = 1000 * 60 * 60; // 1 giờ
    private final Key key = Keys.hmacShaKeyFor(SECRET.getBytes());

    // generate token kèm role
    public String generateToken(String username, String role) {
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    public String extractRole(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody().get("role", String.class);
    }

    // public boolean validateToken(String token) {
    // try {
    // Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
    // return true;
    // } catch (JwtException e) {
    // return false;
    // }
    // }
    public boolean validateToken(String token, UserDetails userDetails) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // Kiểm tra username từ token có khớp với UserDetails không
            String username = claims.getSubject();
            if (!username.equals(userDetails.getUsername())) {
                return false;
            }

            // Kiểm tra token còn hạn không
            Date expiration = claims.getExpiration();
            return !expiration.before(new Date());

        } catch (JwtException e) {
            return false;
        }
    }
}
