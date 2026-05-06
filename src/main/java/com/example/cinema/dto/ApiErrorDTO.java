package com.example.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@AllArgsConstructor
@Builder
public class ApiErrorDTO {
    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
}
