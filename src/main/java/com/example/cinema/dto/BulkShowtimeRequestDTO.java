package com.example.cinema.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class BulkShowtimeRequestDTO {
    private Long movieId;
    private Long roomId;
    private List<Long> roomIds;
    private LocalDate showDate;
    private List<String> sessions;
    private List<String> timeSlots;
    private Double price;
    private Integer maxCreatedCount;
}
