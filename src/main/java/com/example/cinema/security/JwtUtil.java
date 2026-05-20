package com.example.cinema.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateSessionId() {
        return UUID.randomUUID().toString();
    }

    public String generateAccessToken(String identifier, String role, String fullName, String sessionId) {
        return Jwts.builder()
                .setSubject(identifier)
                .claim("type", "access")
                .claim("role", role)
                .claim("fullName", fullName)
                .claim("sessionId", sessionId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public String generateRefreshToken(String identifier, String sessionId) {
        return Jwts.builder()
                .setSubject(identifier)
                .claim("type", "refresh")
                .claim("sessionId", sessionId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractIdentifier(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public String extractSessionId(String token) {
        return parseClaims(token).get("sessionId", String.class);
    }

    public String extractTokenType(String token) {
        return parseClaims(token).get("type", String.class);
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(extractTokenType(token));
    }

    public boolean validateToken(String token, String expectedIdentifier, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            String identifier = claims.getSubject();
            String tokenType = claims.get("type", String.class);

            if (!expectedIdentifier.equals(identifier)) {
                return false;
            }

            if (!expectedType.equals(tokenType)) {
                return false;
            }

            return claims.getExpiration().after(new Date());
        } catch (JwtException e) {
            System.out.println("JWT ERROR: " + e.getMessage());
            return false;
        }
    }
}
