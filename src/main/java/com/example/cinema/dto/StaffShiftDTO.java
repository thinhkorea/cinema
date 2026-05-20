package com.example.cinema.dto;

import com.example.cinema.domain.StaffShift;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.Duration;

@Data
@Builder
public class StaffShiftDTO {
    private Long shiftId;
    private String staffName;
    private String status;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private Long durationSeconds;
    private Double expectedCash;
    private Double actualCash;
    private Double cashDifference;
    private String note;

    public static StaffShiftDTO fromEntity(StaffShift shift) {
        if (shift == null) {
            return null;
        }
        return StaffShiftDTO.builder()
                .shiftId(shift.getShiftId())
                .staffName(resolveStaffName(shift))
                .status(shift.getStatus() == null ? null : shift.getStatus().name())
                .openedAt(shift.getOpenedAt())
                .closedAt(shift.getClosedAt())
                .durationSeconds(resolveDurationSeconds(shift))
                .expectedCash(shift.getExpectedCash())
                .actualCash(shift.getActualCash())
                .cashDifference(shift.getCashDifference())
                .note(shift.getNote())
                .build();
    }

    private static Long resolveDurationSeconds(StaffShift shift) {
        if (shift.getOpenedAt() == null || shift.getClosedAt() == null) {
            return null;
        }
        return Math.max(0, Duration.between(shift.getOpenedAt(), shift.getClosedAt()).getSeconds());
    }

    private static String resolveStaffName(StaffShift shift) {
        if (shift.getStaff() == null || shift.getStaff().getUser() == null) {
            return "Không xác định";
        }
        String fullName = shift.getStaff().getUser().getFullName();
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        String email = shift.getStaff().getUser().getEmail();
        if (email != null && !email.isBlank()) {
            return email;
        }
        String phone = shift.getStaff().getUser().getPhone();
        return phone == null || phone.isBlank() ? "Không xác định" : phone;
    }
}
