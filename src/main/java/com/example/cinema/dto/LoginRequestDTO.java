package com.example.cinema.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginRequestDTO {
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
