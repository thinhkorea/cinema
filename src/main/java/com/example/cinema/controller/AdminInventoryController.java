package com.example.cinema.controller;

import com.example.cinema.domain.Ingredient;
import com.example.cinema.domain.IngredientBatch;
import com.example.cinema.dto.IngredientBatchDTO;
import com.example.cinema.dto.IngredientConsumeRequestDTO;
import com.example.cinema.dto.IngredientDTO;
import com.example.cinema.dto.IngredientStockMovementDTO;
import com.example.cinema.dto.ReceiveIngredientBatchRequestDTO;
import com.example.cinema.dto.SnackRecipeItemDTO;
import com.example.cinema.domain.Snack;
import com.example.cinema.dto.SnackRecipeItemRequestDTO;
import com.example.cinema.service.InventoryService;
import com.example.cinema.repository.SnackRepository;
import com.example.cinema.domain.Snack;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInventoryController {

    private final InventoryService inventoryService;
    private final SnackRepository snackRepository;

    private boolean isPopcornSnack(Snack snack) {
        if (snack == null || snack.getSnackName() == null) {
            return false;
        }
        return snack.getSnackName().toLowerCase().contains("bắp");
    }

    @GetMapping("/ingredients")
    public ResponseEntity<List<IngredientDTO>> getIngredients() {
        return ResponseEntity.ok(inventoryService.getIngredients());
    }

    @GetMapping("/ingredients/low-stock")
    public ResponseEntity<List<IngredientDTO>> getLowStockIngredients(
            @RequestParam(defaultValue = "10") Double threshold) {
        return ResponseEntity.ok(inventoryService.getLowStockIngredients(threshold));
    }

    @GetMapping("/ingredients/expiring-batches")
    public ResponseEntity<List<IngredientBatchDTO>> getExpiringBatches(
            @RequestParam(defaultValue = "7") Integer days) {
        return ResponseEntity.ok(inventoryService.getExpiringBatches(days));
    }

    @GetMapping("/ingredients/expired-batches")
    public ResponseEntity<List<IngredientBatchDTO>> getExpiredBatches() {
        return ResponseEntity.ok(inventoryService.getExpiredBatches());
    }

    @PostMapping("/ingredients")
    public ResponseEntity<?> createIngredient(@RequestBody Ingredient ingredient) {
        try {
            IngredientDTO created = inventoryService.createIngredientDTO(ingredient);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/ingredients/{ingredientId}")
    public ResponseEntity<?> updateIngredient(
            @PathVariable Long ingredientId,
            @RequestBody Ingredient ingredient) {
        try {
            IngredientDTO updated = inventoryService.updateIngredientDTO(ingredientId, ingredient);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/ingredients/{ingredientId}")
    public ResponseEntity<?> deleteIngredient(@PathVariable Long ingredientId) {
        try {
            inventoryService.deleteIngredient(ingredientId);
            return ResponseEntity.ok(Map.of("message", "Đã xóa nguyên liệu."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/ingredients/{ingredientId}/batches")
        public ResponseEntity<?> receiveBatch(
            @PathVariable Long ingredientId,
            @RequestBody ReceiveIngredientBatchRequestDTO request,
            Authentication authentication) {
        try {
            String actor = authentication != null ? authentication.getName() : "admin";
            IngredientBatch created = inventoryService.receiveBatch(ingredientId, request, actor);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/ingredients/{ingredientId}/batches")
    public ResponseEntity<List<IngredientBatchDTO>> getBatchesByIngredient(@PathVariable Long ingredientId) {
        return ResponseEntity.ok(inventoryService.getBatchesByIngredient(ingredientId));
    }

    @PostMapping("/ingredients/batches/{batchId}/discard-expired")
    public ResponseEntity<?> discardExpiredBatch(
            @PathVariable Long batchId,
            Authentication authentication) {
        try {
            String actor = authentication != null ? authentication.getName() : "admin";
            inventoryService.discardExpiredBatch(batchId, actor);
            return ResponseEntity.ok(Map.of("message", "Đã loại bỏ lô hết hạn."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/ingredients/batches/discard-expired-all")
    public ResponseEntity<?> discardAllExpiredBatches(Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : "admin";
        int discardedCount = inventoryService.discardAllExpiredBatches(actor);
        return ResponseEntity.ok(Map.of(
                "message", "Đã loại bỏ các lô hết hạn.",
                "discardedCount", discardedCount));
    }

    @GetMapping("/ingredients/{ingredientId}/movements")
    public ResponseEntity<List<IngredientStockMovementDTO>> getIngredientMovements(@PathVariable Long ingredientId) {
        return ResponseEntity.ok(inventoryService.getIngredientMovements(ingredientId));
    }

    @PostMapping("/ingredients/{ingredientId}/consume")
        public ResponseEntity<?> consumeIngredient(
            @PathVariable Long ingredientId,
            @RequestBody IngredientConsumeRequestDTO request,
            Authentication authentication) {
        try {
            String actor = authentication != null ? authentication.getName() : "admin";
            inventoryService.consumeIngredientByStaff(ingredientId, request, actor);
            return ResponseEntity.ok(Map.of("message", "Đã ghi nhận xuất dùng nguyên liệu."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/snacks/{snackId}/recipe")
    public ResponseEntity<List<SnackRecipeItemDTO>> getRecipeBySnack(@PathVariable Long snackId) {
        try {
            Snack snack = snackRepository.findById(snackId)
                    .orElseThrow(() -> new IllegalArgumentException("Snack not found: " + snackId));
            if (snack.getCategory() != Snack.SnackCategory.SNACK || !isPopcornSnack(snack)) {
                return ResponseEntity.badRequest().body(null);
            }
            return ResponseEntity.ok(inventoryService.getRecipeBySnack(snackId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PutMapping("/snacks/{snackId}/recipe")
    public ResponseEntity<?> replaceRecipe(
            @PathVariable Long snackId,
            @RequestBody List<SnackRecipeItemRequestDTO> request) {
        try {
            Snack snack = snackRepository.findById(snackId)
                    .orElseThrow(() -> new IllegalArgumentException("Snack not found: " + snackId));
            if (snack.getCategory() != Snack.SnackCategory.SNACK || !isPopcornSnack(snack)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Công thức chỉ áp dụng cho bắp rang."));
            }
            List<SnackRecipeItemDTO> updated = inventoryService.replaceRecipe(snackId, request);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/snacks/{snackId}/instructions")
    public ResponseEntity<?> getSnackInstructions(@PathVariable Long snackId) {
        try {
            Snack snack = snackRepository.findById(snackId)
                    .orElseThrow(() -> new IllegalArgumentException("Snack not found: " + snackId));
            if (snack.getCategory() != Snack.SnackCategory.SNACK || !isPopcornSnack(snack)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Chỉ có bắp rang mới có công thức."));
            }
            String instructions = inventoryService.getRecipeInstructions(snackId);
            return ResponseEntity.ok(Map.of("instructions", instructions == null ? "" : instructions));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/snacks/{snackId}/instructions")
    public ResponseEntity<?> updateSnackInstructions(
            @PathVariable Long snackId,
            @RequestBody Map<String, String> body) {
        try {
            Snack snack = snackRepository.findById(snackId)
                    .orElseThrow(() -> new IllegalArgumentException("Snack not found: " + snackId));
            if (snack.getCategory() != Snack.SnackCategory.SNACK || !isPopcornSnack(snack)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Chỉ có bắp rang mới có công thức."));
            }
            String instructions = body.getOrDefault("instructions", "");
            Snack updated = inventoryService.updateRecipeInstructions(snackId, instructions);
            return ResponseEntity.ok(Map.of("message", "OK", "snackId", updated.getSnackId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
