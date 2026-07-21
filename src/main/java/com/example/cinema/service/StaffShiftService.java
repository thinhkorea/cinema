package com.example.cinema.service;

import com.example.cinema.domain.Staff;
import com.example.cinema.domain.StaffShift;
import com.example.cinema.domain.StaffShiftCapacity;
import com.example.cinema.dto.ShiftRevenueItemDTO;
import com.example.cinema.dto.StaffOptionDTO;
import com.example.cinema.dto.StaffShiftAssignRequestDTO;
import com.example.cinema.dto.StaffShiftCapacityDTO;
import com.example.cinema.dto.StaffShiftCapacityRequestDTO;
import com.example.cinema.dto.StaffShiftDTO;
import com.example.cinema.dto.StaffShiftRegisterRequestDTO;
import com.example.cinema.repository.StaffRepository;
import com.example.cinema.repository.StaffShiftCapacityRepository;
import com.example.cinema.repository.StaffShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StaffShiftService {

    private static final int MAX_SHIFTS_PER_DAY = 2;

    private final StaffRepository staffRepository;
    private final StaffShiftRepository staffShiftRepository;
    private final StaffShiftCapacityRepository staffShiftCapacityRepository;
    private final BookingService bookingService;

    @Transactional(readOnly = true)
    public StaffShiftDTO getOpenShift(String username) {
        Staff staff = resolveStaff(username);
        return staffShiftRepository.findFirstByStaffAndStatusOrderByOpenedAtDesc(staff, StaffShift.Status.OPEN)
                .map(StaffShiftDTO::fromEntity)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<StaffOptionDTO> getActiveStaffOptions() {
        return staffRepository.findAll().stream()
                .filter(staff -> staff.getStatus() == Staff.Status.ACTIVE)
                .map(staff -> StaffOptionDTO.builder()
                        .staffId(staff.getStaffId())
                        .staffName(resolveStaffName(staff))
                        .position(staff.getPosition())
                        .build())
                .toList();
    }

    @Transactional
    public Map<String, Object> assignShifts(StaffShiftAssignRequestDTO request) {
        if (request == null || request.getWorkDate() == null) {
            throw new IllegalArgumentException("Vui lòng chọn ngày làm việc.");
        }
        if (request.getStaffIds() == null || request.getStaffIds().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất một nhân viên.");
        }

        StaffShift.ShiftSlot shiftSlot = parseShiftSlot(request.getShiftSlot());
        ShiftSchedule schedule = buildSchedule(request.getWorkDate(), shiftSlot);
        StaffShiftCapacity capacity = staffShiftCapacityRepository
                .findByWorkDateAndShiftSlot(request.getWorkDate(), shiftSlot)
                .orElseThrow(() -> new IllegalStateException("Vui long tao ca lam truoc khi xep nhan vien."));
        int maxStaff = capacity.getMaxStaff() == null ? 0 : capacity.getMaxStaff();

        int assignedCount = 0;
        int skippedCount = 0;
        List<String> skippedMessages = new java.util.ArrayList<>();

        for (Long staffId : request.getStaffIds()) {
            if (staffId == null) {
                skippedCount++;
                skippedMessages.add("Bỏ qua một nhân viên không hợp lệ.");
                continue;
            }

            Staff staff = staffRepository.findById(staffId)
                    .orElse(null);
            if (staff == null || staff.getStatus() != Staff.Status.ACTIVE) {
                skippedCount++;
                skippedMessages.add("Không tìm thấy nhân viên hợp lệ với mã " + staffId + ".");
                continue;
            }

            if (staffShiftRepository.existsByStaffAndWorkDateAndShiftSlot(staff, request.getWorkDate(), shiftSlot)) {
                skippedCount++;
                skippedMessages.add(resolveStaffName(staff) + " đã được xếp ở ca này.");
                continue;
            }

            if (staffShiftRepository.countByStaffAndWorkDate(staff, request.getWorkDate()) >= MAX_SHIFTS_PER_DAY) {
                skippedCount++;
                skippedMessages.add(resolveStaffName(staff) + " đã đủ 2 ca trong ngày.");
                continue;
            }

            long registeredCount = staffShiftRepository.countByWorkDateAndShiftSlot(request.getWorkDate(), shiftSlot);
            if (registeredCount >= maxStaff) {
                skippedCount++;
                skippedMessages.add("Ca " + resolveShiftLabel(shiftSlot) + " da du " + maxStaff + " nhan vien.");
                continue;
            }

            StaffShift shift = new StaffShift();
            shift.setStaff(staff);
            shift.setWorkDate(request.getWorkDate());
            shift.setShiftSlot(shiftSlot);
            shift.setScheduledStart(schedule.start());
            shift.setScheduledEnd(schedule.end());
            shift.setStatus(StaffShift.Status.ASSIGNED);
            shift.setAttendanceStatus(StaffShift.AttendanceStatus.ASSIGNED);
            shift.setExpectedCash(0.0);
            shift.setActualCash(0.0);
            shift.setCashDifference(0.0);
            shift.setLateMinutes(0);
            shift.setEarlyLeaveMinutes(0);
            shift.setOvertimeMinutes(0);
            staffShiftRepository.save(shift);
            assignedCount++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workDate", request.getWorkDate());
        result.put("shiftSlot", shiftSlot.name());
        result.put("shiftLabel", resolveShiftLabel(shiftSlot));
        result.put("assignedCount", assignedCount);
        result.put("skippedCount", skippedCount);
        result.put("skippedMessages", skippedMessages);
        return result;
    }

    @Transactional
    public StaffShiftDTO startShift(String username) {
        Staff staff = resolveStaff(username);
        staffShiftRepository.findFirstByStaffAndStatusOrderByOpenedAtDesc(staff, StaffShift.Status.OPEN)
                .ifPresent(open -> {
                    throw new IllegalStateException("Bạn đang có một ca chưa kết thúc.");
                });

        LocalDateTime now = LocalDateTime.now();
        LocalDate workDate = now.toLocalDate();
        StaffShift.ShiftSlot shiftSlot = resolveShiftSlot(now.toLocalTime());
        if (shiftSlot == null) {
            throw new IllegalStateException("Hiện không nằm trong khung giờ làm việc của rạp.");
        }

        StaffShift shift = staffShiftRepository
                .findFirstByStaffAndWorkDateAndShiftSlotAndStatus(staff, workDate, shiftSlot, StaffShift.Status.ASSIGNED)
                .orElseThrow(() -> new IllegalStateException("Bạn chưa được xếp ca cho khung giờ hiện tại."));

        shift.setOpenedAt(now);
        shift.setStatus(StaffShift.Status.OPEN);
        shift.setAttendanceStatus(StaffShift.AttendanceStatus.ON_TIME);
        shift.setLateMinutes(calculateLateMinutes(shift.getScheduledStart(), now));
        shift.setEarlyLeaveMinutes(0);
        shift.setOvertimeMinutes(0);
        if (shift.getLateMinutes() > 0) {
            shift.setAttendanceStatus(StaffShift.AttendanceStatus.LATE);
        }
        return StaffShiftDTO.fromEntity(staffShiftRepository.save(shift));
    }

    @Transactional
    public StaffShiftDTO closeShift(String username, Double actualCash, String note) {
        Staff staff = resolveStaff(username);
        StaffShift shift = staffShiftRepository.findFirstByStaffAndStatusOrderByOpenedAtDesc(staff, StaffShift.Status.OPEN)
                .orElseThrow(() -> new IllegalStateException("Bạn chưa vào ca nên không thể kết ca."));

        LocalDateTime closedAt = LocalDateTime.now();
        double safeActualCash = actualCash == null ? 0.0 : actualCash;
        double expectedCash = getExpectedCashForShift(username, shift.getOpenedAt(), closedAt);

        shift.setExpectedCash(expectedCash);
        shift.setActualCash(safeActualCash);
        shift.setCashDifference(safeActualCash - expectedCash);
        shift.setNote(note);
        shift.setClosedAt(closedAt);
        shift.setStatus(StaffShift.Status.CLOSED);
        shift.setEarlyLeaveMinutes(calculateEarlyLeaveMinutes(shift.getScheduledEnd(), closedAt));
        shift.setOvertimeMinutes(calculateOvertimeMinutes(shift.getScheduledEnd(), closedAt));
        shift.setAttendanceStatus(resolveAttendanceStatus(shift));
        return StaffShiftDTO.fromEntity(staffShiftRepository.save(shift));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildShiftCloseReport(String username, LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        Staff staff = resolveStaff(username);
        List<ShiftRevenueItemDTO> revenues = bookingService.getDailyShiftRevenueForStaff(target, username);
        StaffShiftDTO openShift = staffShiftRepository
                .findFirstByStaffAndStatusOrderByOpenedAtDesc(staff, StaffShift.Status.OPEN)
                .map(StaffShiftDTO::fromEntity)
                .orElse(null);

        List<StaffShiftDTO> workedShifts = staffShiftRepository.findByStaffAndWorkDateOrderByScheduledStartAsc(staff, target)
                .stream()
                .filter(shift -> shift.getStatus() == StaffShift.Status.OPEN || shift.getStatus() == StaffShift.Status.CLOSED)
                .map(StaffShiftDTO::fromEntity)
                .toList();

        long dailySeconds = workedShifts.stream()
                .map(StaffShiftDTO::getDurationSeconds)
                .filter(value -> value != null && value > 0)
                .mapToLong(Long::longValue)
                .sum();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("date", target);
        report.put("staff", username);
        report.put("openShift", openShift);
        report.put("shifts", workedShifts);
        report.put("revenues", revenues);
        report.put("dailyShiftCount", workedShifts.size());
        report.put("dailyDurationSeconds", dailySeconds);
        return report;
    }

    @Transactional(readOnly = true)
    public List<StaffShiftDTO> getDailyShifts(LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        return staffShiftRepository.findByWorkDateOrderByScheduledStartAsc(target)
                .stream()
                .map(StaffShiftDTO::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StaffShiftCapacityDTO> getDailyShiftCapacities(LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        return staffShiftCapacityRepository.findByWorkDateOrderByShiftSlotAsc(target)
                .stream()
                .map(capacity -> StaffShiftCapacityDTO.fromEntity(
                        capacity,
                        staffShiftRepository.countByWorkDateAndShiftSlot(target, capacity.getShiftSlot()),
                        false))
                .toList();
    }

    @Transactional
    public StaffShiftCapacityDTO saveShiftCapacity(StaffShiftCapacityRequestDTO request) {
        if (request == null || request.getWorkDate() == null) {
            throw new IllegalArgumentException("Vui long chon ngay lam viec.");
        }
        StaffShift.ShiftSlot shiftSlot = parseShiftSlot(request.getShiftSlot());
        int maxStaff = request.getMaxStaff() == null ? 0 : request.getMaxStaff();
        if (maxStaff < 1 || maxStaff > 50) {
            throw new IllegalArgumentException("So nhan vien toi da phai tu 1 den 50.");
        }

        long registeredCount = staffShiftRepository.countByWorkDateAndShiftSlot(request.getWorkDate(), shiftSlot);
        if (maxStaff < registeredCount) {
            throw new IllegalStateException("Ca nay dang co " + registeredCount
                    + " nhan vien, khong the giam gioi han xuong " + maxStaff + ".");
        }

        StaffShiftCapacity capacity = staffShiftCapacityRepository
                .findByWorkDateAndShiftSlot(request.getWorkDate(), shiftSlot)
                .orElseGet(StaffShiftCapacity::new);
        capacity.setWorkDate(request.getWorkDate());
        capacity.setShiftSlot(shiftSlot);
        capacity.setMaxStaff(maxStaff);

        StaffShiftCapacity saved = staffShiftCapacityRepository.save(capacity);
        return StaffShiftCapacityDTO.fromEntity(saved, registeredCount, false);
    }

    @Transactional(readOnly = true)
    public List<StaffShiftCapacityDTO> getShiftRegistrationOptions(String username, LocalDate date) {
        Staff staff = resolveStaff(username);
        LocalDate target = date == null ? LocalDate.now() : date;
        return staffShiftCapacityRepository.findByWorkDateOrderByShiftSlotAsc(target)
                .stream()
                .map(capacity -> StaffShiftCapacityDTO.fromEntity(
                        capacity,
                        staffShiftRepository.countByWorkDateAndShiftSlot(target, capacity.getShiftSlot()),
                        staffShiftRepository.existsByStaffAndWorkDateAndShiftSlot(staff, target, capacity.getShiftSlot())))
                .toList();
    }

    @Transactional
    public StaffShiftDTO registerShift(String username, StaffShiftRegisterRequestDTO request) {
        if (request == null || request.getWorkDate() == null) {
            throw new IllegalArgumentException("Vui long chon ngay lam viec.");
        }

        Staff staff = resolveStaff(username);
        if (staff.getStatus() != Staff.Status.ACTIVE) {
            throw new IllegalStateException("Tai khoan nhan vien chua hoat dong.");
        }

        StaffShift.ShiftSlot shiftSlot = parseShiftSlot(request.getShiftSlot());
        ShiftSchedule schedule = buildSchedule(request.getWorkDate(), shiftSlot);
        LocalDateTime now = LocalDateTime.now();
        if (request.getWorkDate().isBefore(now.toLocalDate()) || !schedule.end().isAfter(now)) {
            throw new IllegalStateException("Khong the dang ky ca da ket thuc.");
        }

        StaffShiftCapacity capacity = staffShiftCapacityRepository
                .findLockedByWorkDateAndShiftSlot(request.getWorkDate(), shiftSlot)
                .orElseThrow(() -> new IllegalStateException("Ca nay chua duoc admin mo dang ky."));
        int maxStaff = capacity.getMaxStaff() == null ? 0 : capacity.getMaxStaff();
        if (maxStaff < 1) {
            throw new IllegalStateException("Ca nay dang dong dang ky.");
        }

        if (staffShiftRepository.existsByStaffAndWorkDateAndShiftSlot(staff, request.getWorkDate(), shiftSlot)) {
            throw new IllegalStateException("Ban da dang ky hoac duoc xep vao ca nay.");
        }
        if (staffShiftRepository.countByStaffAndWorkDate(staff, request.getWorkDate()) >= MAX_SHIFTS_PER_DAY) {
            throw new IllegalStateException("Bạn chỉ được đăng ký tối đa 2 ca trong ngày.");
        }

        long registeredCount = staffShiftRepository.countByWorkDateAndShiftSlot(request.getWorkDate(), shiftSlot);
        if (registeredCount >= maxStaff) {
            throw new IllegalStateException("Ca nay da du " + maxStaff + " nhan vien.");
        }

        StaffShift shift = new StaffShift();
        shift.setStaff(staff);
        shift.setWorkDate(request.getWorkDate());
        shift.setShiftSlot(shiftSlot);
        shift.setScheduledStart(schedule.start());
        shift.setScheduledEnd(schedule.end());
        shift.setStatus(StaffShift.Status.ASSIGNED);
        shift.setAttendanceStatus(StaffShift.AttendanceStatus.ASSIGNED);
        shift.setExpectedCash(0.0);
        shift.setActualCash(0.0);
        shift.setCashDifference(0.0);
        shift.setLateMinutes(0);
        shift.setEarlyLeaveMinutes(0);
        shift.setOvertimeMinutes(0);
        return StaffShiftDTO.fromEntity(staffShiftRepository.save(shift));
    }

    private double getExpectedCashForShift(String username, LocalDateTime openedAt, LocalDateTime closedAt) {
        return bookingService.getRevenueForStaffBetween(openedAt, closedAt, username).stream()
                .mapToDouble(row -> value(row.getTicketCashRevenue()) + value(row.getPopcornCashRevenue()))
                .sum();
    }

    private StaffShift.ShiftSlot resolveShiftSlot(LocalTime time) {
        if (time.isBefore(LocalTime.of(7, 30))) {
            return null;
        }
        if (time.isBefore(LocalTime.of(11, 30))) {
            return StaffShift.ShiftSlot.SHIFT_1;
        }
        if (time.isBefore(LocalTime.of(15, 30))) {
            return StaffShift.ShiftSlot.SHIFT_2;
        }
        if (time.isBefore(LocalTime.of(19, 30))) {
            return StaffShift.ShiftSlot.SHIFT_3;
        }
        return StaffShift.ShiftSlot.SHIFT_4;
    }

    private StaffShift.ShiftSlot parseShiftSlot(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Vui lòng chọn ca làm.");
        }
        try {
            return StaffShift.ShiftSlot.valueOf(rawValue.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Ca làm không hợp lệ.");
        }
    }

    private int calculateLateMinutes(LocalDateTime scheduledStart, LocalDateTime actualStart) {
        if (scheduledStart == null || actualStart == null || !actualStart.isAfter(scheduledStart)) {
            return 0;
        }
        return (int) Duration.between(scheduledStart, actualStart).toMinutes();
    }

    private int calculateEarlyLeaveMinutes(LocalDateTime scheduledEnd, LocalDateTime actualEnd) {
        if (scheduledEnd == null || actualEnd == null || !actualEnd.isBefore(scheduledEnd)) {
            return 0;
        }
        return (int) Duration.between(actualEnd, scheduledEnd).toMinutes();
    }

    private int calculateOvertimeMinutes(LocalDateTime scheduledEnd, LocalDateTime actualEnd) {
        if (scheduledEnd == null || actualEnd == null || !actualEnd.isAfter(scheduledEnd)) {
            return 0;
        }
        return (int) Duration.between(scheduledEnd, actualEnd).toMinutes();
    }

    private StaffShift.AttendanceStatus resolveAttendanceStatus(StaffShift shift) {
        boolean late = value(shift.getLateMinutes()) > 0;
        boolean early = value(shift.getEarlyLeaveMinutes()) > 0;
        boolean overtime = value(shift.getOvertimeMinutes()) > 0;
        if (late && early) {
            return StaffShift.AttendanceStatus.LATE_AND_EARLY;
        }
        if (late) {
            return StaffShift.AttendanceStatus.LATE;
        }
        if (early) {
            return StaffShift.AttendanceStatus.EARLY_LEAVE;
        }
        if (overtime) {
            return StaffShift.AttendanceStatus.OVERTIME;
        }
        return StaffShift.AttendanceStatus.ON_TIME;
    }

    private int value(Integer minutes) {
        return minutes == null ? 0 : minutes;
    }

    private double value(Double amount) {
        return amount == null ? 0.0 : amount;
    }

    private ShiftSchedule buildSchedule(LocalDate workDate, StaffShift.ShiftSlot shiftSlot) {
        return switch (shiftSlot) {
            case SHIFT_1 ->
                    new ShiftSchedule(workDate.atTime(LocalTime.of(7, 30)), workDate.atTime(LocalTime.of(11, 30)));
            case SHIFT_2 ->
                    new ShiftSchedule(workDate.atTime(LocalTime.of(11, 30)), workDate.atTime(LocalTime.of(15, 30)));
            case SHIFT_3 ->
                    new ShiftSchedule(workDate.atTime(LocalTime.of(15, 30)), workDate.atTime(LocalTime.of(19, 30)));
            case SHIFT_4 ->
                    new ShiftSchedule(workDate.atTime(LocalTime.of(19, 30)), workDate.plusDays(1).atStartOfDay());
        };
    }

    private String resolveShiftLabel(StaffShift.ShiftSlot shiftSlot) {
        return switch (shiftSlot) {
            case SHIFT_1 -> "Ca 1 (07:30 - 11:30)";
            case SHIFT_2 -> "Ca 2 (11:30 - 15:30)";
            case SHIFT_3 -> "Ca 3 (15:30 - 19:30)";
            case SHIFT_4 -> "Ca 4 (19:30 - 00:00)";
        };
    }

    private Staff resolveStaff(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Không xác định được nhân viên.");
        }
        return staffRepository.findByUser_Email(username)
                .or(() -> staffRepository.findByUser_Phone(username))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên: " + username));
    }

    private String resolveStaffName(Staff staff) {
        if (staff == null || staff.getUser() == null) {
            return "Không xác định";
        }
        String fullName = staff.getUser().getFullName();
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        String email = staff.getUser().getEmail();
        if (email != null && !email.isBlank()) {
            return email;
        }
        String phone = staff.getUser().getPhone();
        return phone == null || phone.isBlank() ? "Không xác định" : phone;
    }

    private record ShiftSchedule(LocalDateTime start, LocalDateTime end) {
    }
}
