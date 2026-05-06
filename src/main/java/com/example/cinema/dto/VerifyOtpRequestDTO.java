package com.example.cinema.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VerifyOtpRequestDTO {
    private String email;
    private String otp;
}
