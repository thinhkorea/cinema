package com.example.cinema.controller;

import com.example.cinema.dto.StaffShiftCloseRequestDTO;
import com.example.cinema.service.StaffShiftService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/staff/reports")
@RequiredArgsConstructor
public class StaffShiftReportController {

    private final StaffShiftService staffShiftService;

    @GetMapping("/shift-close")
    public ResponseEntity<?> getShiftCloseReport(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return ResponseEntity.status(401).body(Map.of("error", "Bạn cần đăng nhập để xem báo cáo kết ca."));
        }

        LocalDate target = date == null ? LocalDate.now() : date;
        return ResponseEntity.ok(staffShiftService.buildShiftCloseReport(authentication.getName(), target));
    }

    @GetMapping("/shift/status")
    public ResponseEntity<?> getOpenShift(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return ResponseEntity.status(401).body(Map.of("error", "Bạn cần đăng nhập để xem trạng thái ca."));
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("openShift", staffShiftService.getOpenShift(authentication.getName()));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/shift/start")
    public ResponseEntity<?> startShift(Authentication authentication) {
        try {
            if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
                return ResponseEntity.status(401).body(Map.of("error", "Bạn cần đăng nhập để vào ca."));
            }
            return ResponseEntity.ok(Map.of("shift", staffShiftService.startShift(authentication.getName())));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/shift/close")
    public ResponseEntity<?> closeShift(
            @RequestBody(required = false) StaffShiftCloseRequestDTO request,
            Authentication authentication) {
        try {
            if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
                return ResponseEntity.status(401).body(Map.of("error", "Bạn cần đăng nhập để kết ca."));
            }
            Double actualCash = request == null ? 0.0 : request.getActualCash();
            String note = request == null ? null : request.getNote();
            return ResponseEntity.ok(Map.of(
                    "shift", staffShiftService.closeShift(authentication.getName(), actualCash, note)));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}
