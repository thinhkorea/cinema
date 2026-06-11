package com.example.cinema.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class StaffShiftAssignRequestDTO {
    private LocalDate workDate;
    private String shiftSlot;
    private List<Long> staffIds;
}
