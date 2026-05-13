package com.example.cinema.controller;

import com.example.cinema.domain.Snack;
import com.example.cinema.dto.IngredientConsumeRequestDTO;
import com.example.cinema.dto.IngredientStockMovementDTO;
import com.example.cinema.dto.SnackDTO;
import com.example.cinema.dto.SnackRecipeItemDTO;
import com.example.cinema.repository.SnackRepository;
import com.example.cinema.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/staff/inventory")
@RequiredArgsConstructor
public class StaffInventoryController {

    private final InventoryService inventoryService;
    private final SnackRepository snackRepository;

    private boolean isPopcornSnack(Snack snack) {
        if (snack == null || snack.getSnackName() == null || snack.getCategory() != Snack.SnackCategory.SNACK) {
            return false;
        }

        String name = snack.getSnackName().toLowerCase();
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return normalized.contains("bap") || normalized.contains("popcorn");
    }

    private SnackDTO toSnackDTO(Snack snack) {
        return SnackDTO.builder()
                .snackId(snack.getSnackId())
                .snackName(snack.getSnackName())
                .description(snack.getDescription())
                .price(snack.getPrice())
                .imageUrl(snack.getImageUrl())
                .category(snack.getCategory() == null ? null : snack.getCategory().name())
                .available(snack.getAvailable())
                .warehouseTrackable(snack.getWarehouseTrackable())
                .expiryDate(snack.getExpiryDate())
                .recipeInstructions(snack.getRecipeInstructions())
                .build();
    }

    @GetMapping("/popcorn-snacks")
    public ResponseEntity<List<SnackDTO>> getPopcornSnacks() {
        List<SnackDTO> snacks = snackRepository.findAll().stream()
                .filter(this::isPopcornSnack)
                .map(this::toSnackDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(snacks);
    }

    @GetMapping("/snacks/{snackId}/recipe")
    public ResponseEntity<?> getRecipeBySnack(@PathVariable Long snackId) {
        try {
            Snack snack = snackRepository.findById(snackId)
                    .orElseThrow(() -> new IllegalArgumentException("Snack not found: " + snackId));
            if (!isPopcornSnack(snack)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cong thuc chi ap dung cho bap rang."));
            }

            List<SnackRecipeItemDTO> recipe = inventoryService.getRecipeBySnack(snackId);
            return ResponseEntity.ok(recipe);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/snacks/{snackId}/instructions")
    public ResponseEntity<?> getSnackInstructions(@PathVariable Long snackId) {
        try {
            Snack snack = snackRepository.findById(snackId)
                    .orElseThrow(() -> new IllegalArgumentException("Snack not found: " + snackId));
            if (!isPopcornSnack(snack)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cong thuc chi ap dung cho bap rang."));
            }

            String instructions = inventoryService.getRecipeInstructions(snackId);
            return ResponseEntity.ok(Map.of("instructions", instructions == null ? "" : instructions));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

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
