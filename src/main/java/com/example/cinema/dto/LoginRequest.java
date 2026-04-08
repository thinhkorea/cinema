package com.example.cinema.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String identifier;
    private String username;
    private String password;

    public String getLoginIdentifier() {
        if (identifier != null && !identifier.trim().isEmpty()) {
            return identifier.trim();
        }
        return username != null ? username.trim() : null;
    }
}
