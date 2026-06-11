package com.example.cinema.controller;

import com.example.cinema.dto.ShiftSummaryDTO;
import com.example.cinema.dto.StaffOptionDTO;
import com.example.cinema.dto.StaffShiftAssignRequestDTO;
import com.example.cinema.service.BookingService;
import com.example.cinema.service.InventoryService;
import com.example.cinema.service.SnackService;
import com.example.cinema.service.StaffShiftService;
import com.example.cinema.service.SupplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminShiftReportController {

    private final InventoryService inventoryService;
    private final SupplyService supplyService;
    private final SnackService snackService;
    private final BookingService bookingService;
    private final StaffShiftService staffShiftService;

    @GetMapping("/shift-staff-options")
    public ResponseEntity<List<StaffOptionDTO>> getShiftStaffOptions() {
        return ResponseEntity.ok(staffShiftService.getActiveStaffOptions());
    }

    @PostMapping("/shift-assignments")
    public ResponseEntity<?> assignShifts(@RequestBody StaffShiftAssignRequestDTO request) {
        try {
            return ResponseEntity.ok(staffShiftService.assignShifts(request));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/shift-summary")
    public ResponseEntity<ShiftSummaryDTO> getShiftSummary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate target = date == null ? LocalDate.now() : date;
        ShiftSummaryDTO summary = ShiftSummaryDTO.builder()
            .date(target)
            .ingredients(inventoryService.getDailyIngredientUsage(target))
            .supplies(supplyService.getDailySupplyUsage(target))
            .snackWarehouse(snackService.getDailySnackWarehouseUsage(target))
            .shifts(staffShiftService.getDailyShifts(target))
            .revenues(bookingService.getDailyShiftRevenue(target))
            .build();

        return ResponseEntity.ok(summary);
    }
}
