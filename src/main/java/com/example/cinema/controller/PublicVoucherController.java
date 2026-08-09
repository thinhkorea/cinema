package com.example.cinema.controller;

import com.example.cinema.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/vouchers")
@RequiredArgsConstructor
public class PublicVoucherController {

    private final VoucherService voucherService;

    @GetMapping("/active")
    public ResponseEntity<?> getActiveVouchers() {
        return ResponseEntity.ok(voucherService.getPublicActiveVouchers());
    }
}
