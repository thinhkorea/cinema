package com.example.cinema.controller;

import com.example.cinema.dto.VoucherRequestDTO;
import com.example.cinema.service.VoucherService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/vouchers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVoucherController {

    private final VoucherService voucherService;

    public AdminVoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @GetMapping
    public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(voucherService.getAll());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody VoucherRequestDTO request) {
        try {
            return ResponseEntity.ok(voucherService.create(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{voucherId}")
    public ResponseEntity<?> update(@PathVariable Long voucherId, @RequestBody VoucherRequestDTO request) {
        try {
            return ResponseEntity.ok(voucherService.update(voucherId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{voucherId}/active")
    public ResponseEntity<?> toggleActive(@PathVariable Long voucherId, @RequestBody Map<String, Boolean> body) {
        try {
            boolean active = body.getOrDefault("active", true);
            return ResponseEntity.ok(voucherService.toggleActive(voucherId, active));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{voucherId}")
    public ResponseEntity<?> delete(@PathVariable Long voucherId) {
        try {
            voucherService.delete(voucherId);
            return ResponseEntity.ok(Map.of("message", "Đã xóa voucher"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
