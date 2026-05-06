package com.example.cinema.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegisterRequestDTO {
    private String email;
    private String phone;
    private String password;
    private String fullName;
    private String address;
    private String gender;
    private String cccd;
    private String position;
    private Double salary;
}
