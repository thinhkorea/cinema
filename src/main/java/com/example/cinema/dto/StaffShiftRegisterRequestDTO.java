package com.example.cinema.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class StaffShiftRegisterRequestDTO {
    private LocalDate workDate;
    private String shiftSlot;
}
