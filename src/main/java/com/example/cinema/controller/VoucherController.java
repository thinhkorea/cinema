package com.example.cinema.controller;

import com.example.cinema.dto.VoucherRedeemRequestDTO;
import com.example.cinema.dto.VoucherValidateRequestDTO;
import com.example.cinema.service.VoucherService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/vouchers")
@PreAuthorize("hasRole('CUSTOMER')")
public class VoucherController {

    private final VoucherService voucherService;

    public VoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestBody VoucherValidateRequestDTO request, Authentication authentication) {
        try {
            String username = authentication.getName();
            return ResponseEntity.ok(voucherService.validate(request.getCode(), username, request.getTotalAmount()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/redeem")
    public ResponseEntity<?> redeem(@RequestBody VoucherRedeemRequestDTO request, Authentication authentication) {
        try {
            String username = authentication.getName();
            return ResponseEntity.ok(voucherService.redeem(request, username));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/available")
    public ResponseEntity<?> available(@RequestParam Double totalAmount, Authentication authentication) {
        try {
            String username = authentication.getName();
            return ResponseEntity.ok(voucherService.getAvailable(username, totalAmount));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
