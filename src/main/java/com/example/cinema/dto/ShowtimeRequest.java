package com.example.cinema.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ShowtimeRequest {
    private Long movieId;
    private Long roomId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Double price;
}
