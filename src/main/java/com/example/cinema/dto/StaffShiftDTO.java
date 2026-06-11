package com.example.cinema.dto;

import com.example.cinema.domain.StaffShift;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class StaffShiftDTO {
    private Long shiftId;
    private Long staffId;
    private String staffName;
    private String status;
    private LocalDate workDate;
    private String shiftSlot;
    private String shiftLabel;
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private Long durationSeconds;
    private Double expectedCash;
    private Double actualCash;
    private Double cashDifference;
    private Integer lateMinutes;
    private Integer earlyLeaveMinutes;
    private Integer overtimeMinutes;
    private String attendanceStatus;
    private String note;

    public static StaffShiftDTO fromEntity(StaffShift shift) {
        if (shift == null) {
            return null;
        }
        return StaffShiftDTO.builder()
                .shiftId(shift.getShiftId())
                .staffId(shift.getStaff() == null ? null : shift.getStaff().getStaffId())
                .staffName(resolveStaffName(shift))
                .status(shift.getStatus() == null ? null : shift.getStatus().name())
                .workDate(shift.getWorkDate())
                .shiftSlot(shift.getShiftSlot() == null ? null : shift.getShiftSlot().name())
                .shiftLabel(resolveShiftLabel(shift))
                .scheduledStart(shift.getScheduledStart())
                .scheduledEnd(shift.getScheduledEnd())
                .openedAt(shift.getOpenedAt())
                .closedAt(shift.getClosedAt())
                .durationSeconds(resolveDurationSeconds(shift))
                .expectedCash(shift.getExpectedCash())
                .actualCash(shift.getActualCash())
                .cashDifference(shift.getCashDifference())
                .lateMinutes(shift.getLateMinutes())
                .earlyLeaveMinutes(shift.getEarlyLeaveMinutes())
                .overtimeMinutes(shift.getOvertimeMinutes())
                .attendanceStatus(shift.getAttendanceStatus() == null ? null : shift.getAttendanceStatus().name())
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

    private static String resolveShiftLabel(StaffShift shift) {
        if (shift.getShiftSlot() == null) {
            return "Không xác định";
        }
        return switch (shift.getShiftSlot()) {
            case SHIFT_1 -> "Ca 1 (07:30 - 11:30)";
            case SHIFT_2 -> "Ca 2 (11:30 - 15:30)";
            case SHIFT_3 -> "Ca 3 (15:30 - 19:30)";
            case SHIFT_4 -> "Ca 4 (19:30 - 00:00)";
        };
    }
}
