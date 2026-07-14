package com.example.cinema.controller;

import com.example.cinema.dto.CreateSnackOrderRequestDTO;
import com.example.cinema.dto.SnackOrderResponseDTO;
import com.example.cinema.service.SnackOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/snack-orders")
@RequiredArgsConstructor
public class SnackOrderController {

    private final SnackOrderService snackOrderService;

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody CreateSnackOrderRequestDTO request, Principal principal) {
        try {
            if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Ban can dang nhap bang tai khoan khach hang de dat bap nuoc."));
            }
            SnackOrderResponseDTO result = snackOrderService.createOrder(principal.getName(), request);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/my-orders")
    public ResponseEntity<?> getMyOrders(Principal principal) {
        try {
            if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Ban can dang nhap de xem don bap nuoc."));
            }
            List<SnackOrderResponseDTO> orders = snackOrderService.getMyOrders(principal.getName());
            return ResponseEntity.ok(orders);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/{orderCode}")
    public ResponseEntity<?> getOrderByCode(@PathVariable String orderCode, Principal principal) {
        try {
            String username = principal != null ? principal.getName() : null;
            return ResponseEntity.ok(snackOrderService.getOrderByCode(orderCode, username));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/pay-by-code/{orderCode}")
    public ResponseEntity<?> markPaidByCode(@PathVariable String orderCode) {
        try {
            SnackOrderResponseDTO result = snackOrderService.markPaidByOrderCode(orderCode, "VNPAY");
            return ResponseEntity.ok(Map.of(
                    "message", "Đã cập nhật thanh toán đơn bắp nước thành công!",
                    "orderCode", result.getOrderCode(),
                    "status", result.getStatus()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

}
