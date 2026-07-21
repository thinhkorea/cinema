package com.example.cinema.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class StaffShiftCapacityRequestDTO {
    private LocalDate workDate;
    private String shiftSlot;
    private Integer maxStaff;
}
