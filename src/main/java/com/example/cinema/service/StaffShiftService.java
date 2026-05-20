package com.example.cinema.service;

import com.example.cinema.domain.Staff;
import com.example.cinema.domain.StaffShift;
import com.example.cinema.dto.ShiftRevenueItemDTO;
import com.example.cinema.dto.StaffShiftDTO;
import com.example.cinema.repository.StaffRepository;
import com.example.cinema.repository.StaffShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StaffShiftService {

    private final StaffRepository staffRepository;
    private final StaffShiftRepository staffShiftRepository;
    private final BookingService bookingService;

    @Transactional(readOnly = true)
    public StaffShiftDTO getOpenShift(String username) {
        Staff staff = resolveStaff(username);
        return staffShiftRepository.findFirstByStaffAndStatusOrderByOpenedAtDesc(staff, StaffShift.Status.OPEN)
                .map(StaffShiftDTO::fromEntity)
                .orElse(null);
    }

    @Transactional
    public StaffShiftDTO startShift(String username) {
        Staff staff = resolveStaff(username);
        staffShiftRepository.findFirstByStaffAndStatusOrderByOpenedAtDesc(staff, StaffShift.Status.OPEN)
                .ifPresent(open -> {
                    throw new IllegalStateException("Bạn đang có một ca chưa kết thúc.");
                });

        StaffShift shift = new StaffShift();
        shift.setStaff(staff);
        shift.setOpenedAt(LocalDateTime.now());
        shift.setStatus(StaffShift.Status.OPEN);
        return StaffShiftDTO.fromEntity(staffShiftRepository.save(shift));
    }

    @Transactional
    public StaffShiftDTO closeShift(String username, Double actualCash, String note) {
        Staff staff = resolveStaff(username);
        StaffShift shift = staffShiftRepository.findFirstByStaffAndStatusOrderByOpenedAtDesc(staff, StaffShift.Status.OPEN)
                .orElseThrow(() -> new IllegalStateException("Bạn chưa vào ca nên không thể kết ca."));

        double safeActualCash = actualCash == null ? 0.0 : actualCash;
        double expectedCash = getExpectedCashForShift(username, shift.getOpenedAt(), LocalDateTime.now());
        shift.setExpectedCash(expectedCash);
        shift.setActualCash(safeActualCash);
        shift.setCashDifference(safeActualCash - expectedCash);
        shift.setNote(note);
        shift.setClosedAt(LocalDateTime.now());
        shift.setStatus(StaffShift.Status.CLOSED);
        return StaffShiftDTO.fromEntity(staffShiftRepository.save(shift));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildShiftCloseReport(String username, LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        Staff staff = resolveStaff(username);
        LocalDateTime from = target.atStartOfDay();
        LocalDateTime to = target.plusDays(1).atStartOfDay().minusNanos(1);
        List<ShiftRevenueItemDTO> revenues = bookingService.getDailyShiftRevenueForStaff(target, username);
        StaffShiftDTO openShift = staffShiftRepository
                .findFirstByStaffAndStatusOrderByOpenedAtDesc(staff, StaffShift.Status.OPEN)
                .map(StaffShiftDTO::fromEntity)
                .orElse(null);
        List<StaffShiftDTO> shifts = staffShiftRepository.findByStaffAndOpenedAtBetweenOrderByOpenedAtDesc(staff, from, to)
                .stream()
                .map(StaffShiftDTO::fromEntity)
                .toList();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("date", target);
        report.put("staff", username);
        report.put("openShift", openShift);
        report.put("shifts", shifts);
        report.put("revenues", revenues);
        return report;
    }

    @Transactional(readOnly = true)
    public List<StaffShiftDTO> getDailyShifts(LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        LocalDateTime from = target.atStartOfDay();
        LocalDateTime to = target.plusDays(1).atStartOfDay().minusNanos(1);
        return staffShiftRepository.findByOpenedAtBetweenOrderByOpenedAtDesc(from, to)
                .stream()
                .map(StaffShiftDTO::fromEntity)
                .toList();
    }

    private double getExpectedCashForShift(String username, LocalDateTime openedAt, LocalDateTime closedAt) {
        return bookingService.getRevenueForStaffBetween(openedAt, closedAt, username).stream()
                .mapToDouble(row -> value(row.getTicketCashRevenue()) + value(row.getPopcornCashRevenue()))
                .sum();
    }

    private double value(Double amount) {
        return amount == null ? 0.0 : amount;
    }

    private Staff resolveStaff(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Không xác định được nhân viên.");
        }
        return staffRepository.findByUser_Email(username)
                .or(() -> staffRepository.findByUser_Phone(username))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên: " + username));
    }
}
