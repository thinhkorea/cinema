package com.example.cinema.controller;

import com.example.cinema.domain.SupplyItem;
import com.example.cinema.dto.AdjustSupplyStockRequestDTO;
import com.example.cinema.dto.SupplyItemDTO;
import com.example.cinema.service.SupplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/supplies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSupplyController {

    private final SupplyService supplyService;

    @GetMapping
    public ResponseEntity<List<SupplyItemDTO>> getSupplies() {
        return ResponseEntity.ok(supplyService.getSupplies());
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<SupplyItemDTO>> getLowStockSupplies() {
        return ResponseEntity.ok(supplyService.getLowStockSupplies());
    }

    @GetMapping("/{supplyId}/movements")
    public ResponseEntity<?> getSupplyMovements(@PathVariable Long supplyId) {
        try {
            return ResponseEntity.ok(supplyService.getSupplyMovements(supplyId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createSupply(@RequestBody SupplyItem payload) {
        try {
            return ResponseEntity.ok(supplyService.createSupply(payload));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{supplyId}")
    public ResponseEntity<?> updateSupply(@PathVariable Long supplyId, @RequestBody SupplyItem payload) {
        try {
            return ResponseEntity.ok(supplyService.updateSupply(supplyId, payload));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{supplyId}")
    public ResponseEntity<?> deleteSupply(@PathVariable Long supplyId) {
        try {
            supplyService.deleteSupply(supplyId);
            return ResponseEntity.ok(Map.of("message", "Supply deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{supplyId}/stock")
        public ResponseEntity<?> adjustSupplyStock(
            @PathVariable Long supplyId,
            @RequestBody AdjustSupplyStockRequestDTO request,
            Authentication authentication) {
        try {
            String actor = authentication != null ? authentication.getName() : "admin";
            return ResponseEntity.ok(supplyService.adjustSupplyStock(supplyId, request, actor));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
