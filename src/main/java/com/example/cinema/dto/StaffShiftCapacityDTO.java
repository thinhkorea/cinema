package com.example.cinema.dto;

import com.example.cinema.domain.StaffShift;
import com.example.cinema.domain.StaffShiftCapacity;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class StaffShiftCapacityDTO {
    private Long capacityId;
    private LocalDate workDate;
    private String shiftSlot;
    private String shiftLabel;
    private Integer maxStaff;
    private Long registeredCount;
    private Integer remainingSlots;
    private Boolean registeredByCurrentStaff;

    public static StaffShiftCapacityDTO fromEntity(
            StaffShiftCapacity capacity,
            long registeredCount,
            boolean registeredByCurrentStaff) {
        int maxStaff = capacity.getMaxStaff() == null ? 0 : capacity.getMaxStaff();
        int remainingSlots = Math.max(0, maxStaff - (int) registeredCount);
        StaffShift.ShiftSlot shiftSlot = capacity.getShiftSlot();
        return StaffShiftCapacityDTO.builder()
                .capacityId(capacity.getCapacityId())
                .workDate(capacity.getWorkDate())
                .shiftSlot(shiftSlot == null ? null : shiftSlot.name())
                .shiftLabel(resolveShiftLabel(shiftSlot))
                .maxStaff(maxStaff)
                .registeredCount(registeredCount)
                .remainingSlots(remainingSlots)
                .registeredByCurrentStaff(registeredByCurrentStaff)
                .build();
    }

    private static String resolveShiftLabel(StaffShift.ShiftSlot shiftSlot) {
        if (shiftSlot == null) {
            return "Khong xac dinh";
        }
        return switch (shiftSlot) {
            case SHIFT_1 -> "Ca 1 (07:30 - 11:30)";
            case SHIFT_2 -> "Ca 2 (11:30 - 15:30)";
            case SHIFT_3 -> "Ca 3 (15:30 - 19:30)";
            case SHIFT_4 -> "Ca 4 (19:30 - 00:00)";
        };
    }
}
