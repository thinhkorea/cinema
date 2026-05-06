package com.example.cinema.controller;

import com.example.cinema.dto.IngredientConsumeRequestDTO;
import com.example.cinema.dto.IngredientStockMovementDTO;
import com.example.cinema.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/staff/inventory")
@RequiredArgsConstructor
public class StaffInventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/ingredients/{ingredientId}/consume")
        public ResponseEntity<?> consumeIngredient(
            @PathVariable Long ingredientId,
            @RequestBody IngredientConsumeRequestDTO request,
            Authentication authentication) {
        try {
            String actor = authentication != null ? authentication.getName() : "staff";
            inventoryService.consumeIngredientByStaff(ingredientId, request, actor);
            return ResponseEntity.ok(Map.of("message", "Đã ghi nhận xuất dùng nguyên liệu."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/ingredients/{ingredientId}/movements")
    public ResponseEntity<List<IngredientStockMovementDTO>> getIngredientMovements(@PathVariable Long ingredientId) {
        return ResponseEntity.ok(inventoryService.getIngredientMovements(ingredientId));
    }
}
