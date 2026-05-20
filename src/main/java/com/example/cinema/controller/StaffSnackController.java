package com.example.cinema.controller;

import com.example.cinema.dto.StaffSnackFulfillRequestDTO;
import com.example.cinema.service.SnackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/staff/snacks")
@RequiredArgsConstructor
public class StaffSnackController {

    private final SnackService snackService;

    @PostMapping("/fulfill/{txnRef}")
    public ResponseEntity<?> fulfillSnacks(
            @PathVariable String txnRef,
            @RequestBody(required = false) StaffSnackFulfillRequestDTO request,
            Authentication authentication) {
        try {
            String actor = authentication != null ? authentication.getName() : "staff";
            Long popcornSnackId = request == null ? null : request.getPopcornSnackId();
            String additionalPaymentMethod = request == null ? null : request.getAdditionalPaymentMethod();
            Map<String, Object> result = snackService.fulfillSnacksByTxn(txnRef, actor, popcornSnackId, additionalPaymentMethod);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
