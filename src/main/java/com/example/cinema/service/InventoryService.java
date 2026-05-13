package com.example.cinema.service;

import com.example.cinema.domain.Ingredient;
import com.example.cinema.domain.IngredientBatch;
import com.example.cinema.domain.IngredientStockMovement;
import com.example.cinema.domain.Snack;
import com.example.cinema.domain.SnackRecipeItem;
import com.example.cinema.dto.IngredientConsumeRequestDTO;
import com.example.cinema.dto.IngredientBatchDTO;
import com.example.cinema.dto.IngredientDTO;
import com.example.cinema.dto.IngredientStockMovementDTO;
import com.example.cinema.dto.ReceiveIngredientBatchRequestDTO;
import com.example.cinema.dto.ShiftUsageItemDTO;
import com.example.cinema.dto.SnackRecipeItemDTO;
import com.example.cinema.dto.SnackRecipeItemRequestDTO;
import com.example.cinema.repository.IngredientBatchRepository;
import com.example.cinema.repository.IngredientRepository;
import com.example.cinema.repository.IngredientStockMovementRepository;
import com.example.cinema.repository.SnackRecipeItemRepository;
import com.example.cinema.repository.SnackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final IngredientRepository ingredientRepository;
    private final IngredientBatchRepository ingredientBatchRepository;
    private final IngredientStockMovementRepository ingredientStockMovementRepository;
    private final SnackRepository snackRepository;
    private final SnackRecipeItemRepository snackRecipeItemRepository;

    public List<IngredientDTO> getIngredients() {
        LocalDate today = LocalDate.now();
        return ingredientRepository.findAll().stream()
            .map(ingredient -> toIngredientDTO(ingredient, calculateUsableStock(ingredient, today)))
                .collect(Collectors.toList());
    }

    // helper to create ingredient and return DTO
    public IngredientDTO createIngredientDTO(Ingredient ingredient) {
        Ingredient created = createIngredient(ingredient);
        double display = created.getStock() == null ? 0.0 : created.getStock();
        return toIngredientDTO(created, display);
    }

    // helper to update ingredient and return DTO
    public IngredientDTO updateIngredientDTO(Long ingredientId, Ingredient ingredient) {
        Ingredient updated = updateIngredient(ingredientId, ingredient);
        double display = updated.getStock() == null ? 0.0 : updated.getStock();
        return toIngredientDTO(updated, display);
    }

    @Transactional
    public void deleteIngredient(Long ingredientId) {
        Ingredient ingredient = getIngredientEntity(ingredientId);

        if (snackRecipeItemRepository.existsByIngredient_IngredientId(ingredientId)) {
            snackRecipeItemRepository.deleteByIngredient_IngredientId(ingredientId);
        }

        if (ingredientBatchRepository.existsByIngredient_IngredientId(ingredientId)) {
            ingredientBatchRepository.deleteByIngredient_IngredientId(ingredientId);
        }

        if (ingredientStockMovementRepository.existsByIngredient_IngredientId(ingredientId)) {
            ingredientStockMovementRepository.deleteByIngredient_IngredientId(ingredientId);
        }

        ingredientRepository.delete(ingredient);
    }

    public List<IngredientDTO> getLowStockIngredients(Double threshold) {
        double safeThreshold = threshold == null ? 10.0 : Math.max(0.0, threshold);
        LocalDate today = LocalDate.now();
        return ingredientRepository.findAll().stream()
            .map(ingredient -> toIngredientDTO(ingredient, calculateUsableStock(ingredient, today)))
            .filter(i -> i.getStock() != null && i.getStock() <= safeThreshold)
                .collect(Collectors.toList());
    }

    public List<IngredientBatchDTO> getExpiringBatches(Integer days) {
        int safeDays = days == null ? 7 : Math.max(0, days);
        LocalDate today = LocalDate.now();
        LocalDate dueDate = LocalDate.now().plusDays(safeDays);
        return ingredientBatchRepository.findAll().stream()
                .filter(batch -> {
                    double remaining = batch.getQuantityRemaining() == null ? 0.0 : batch.getQuantityRemaining();
                    return remaining > 0;
                })
                .filter(batch -> {
                    LocalDate effectiveExpiryDate = getEffectiveExpiryDate(batch);
                    return effectiveExpiryDate != null
                            && !effectiveExpiryDate.isBefore(today)
                            && !effectiveExpiryDate.isAfter(dueDate);
                })
                .sorted((a, b) -> {
                    LocalDate aDate = getEffectiveExpiryDate(a);
                    LocalDate bDate = getEffectiveExpiryDate(b);
                    if (aDate == null && bDate == null) {
                        return 0;
                    }
                    if (aDate == null) {
                        return 1;
                    }
                    if (bDate == null) {
                        return -1;
                    }
                    return aDate.compareTo(bDate);
                })
                .map(this::toBatchDTO)
                .collect(Collectors.toList());
    }

    public List<IngredientBatchDTO> getExpiredBatches() {
        LocalDate today = LocalDate.now();
        return ingredientBatchRepository.findAll().stream()
                .filter(batch -> {
                    double remaining = batch.getQuantityRemaining() == null ? 0.0 : batch.getQuantityRemaining();
                    return remaining > 0;
                })
                .filter(batch -> isExpiredBatch(batch, today))
                .sorted((a, b) -> {
                    LocalDate aDate = getEffectiveExpiryDate(a);
                    LocalDate bDate = getEffectiveExpiryDate(b);
                    if (aDate == null && bDate == null) {
                        return 0;
                    }
                    if (aDate == null) {
                        return 1;
                    }
                    if (bDate == null) {
                        return -1;
                    }
                    return aDate.compareTo(bDate);
                })
                .map(this::toBatchDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public Ingredient createIngredient(Ingredient ingredient) {
        String name = ingredient.getIngredientName();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("TÃªn nguyÃªn liá»‡u khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng.");
        }
        String unit = ingredient.getUnit();
        if (unit == null || unit.trim().isEmpty()) {
            throw new IllegalArgumentException("ÄÆ¡n vá»‹ khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng.");
        }
        String normalizedName = name.trim();
        String normalizedUnit = unit.trim();
        if (ingredientRepository.existsByIngredientNameIgnoreCase(normalizedName)) {
            throw new IllegalArgumentException("TÃªn nguyÃªn liá»‡u Ä‘Ã£ tá»“n táº¡i.");
        }

        ingredient.setIngredientId(null);
        ingredient.setIngredientName(normalizedName);
        ingredient.setUnit(normalizedUnit);
        if (ingredient.getStock() == null) {
            ingredient.setStock(0.0);
        }
        if (ingredient.getStock() < 0) {
            throw new IllegalArgumentException("Tá»“n kho khÃ´ng Ä‘Æ°á»£c Ã¢m.");
        }
        if (ingredient.getActive() == null) {
            ingredient.setActive(true);
        }
        return ingredientRepository.save(ingredient);
    }

    @Transactional
    public Ingredient updateIngredient(Long ingredientId, Ingredient payload) {
        Ingredient ingredient = getIngredientEntity(ingredientId);
        String name = payload.getIngredientName();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("TÃªn nguyÃªn liá»‡u khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng.");
        }
        String unit = payload.getUnit();
        if (unit == null || unit.trim().isEmpty()) {
            throw new IllegalArgumentException("ÄÆ¡n vá»‹ khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng.");
        }
        String normalizedName = name.trim();
        String normalizedUnit = unit.trim();
        if (ingredientRepository.existsByIngredientNameIgnoreCaseAndIngredientIdNot(normalizedName, ingredientId)) {
            throw new IllegalArgumentException("TÃªn nguyÃªn liá»‡u Ä‘Ã£ tá»“n táº¡i.");
        }

        ingredient.setIngredientName(normalizedName);
        ingredient.setUnit(normalizedUnit);
        ingredient.setActive(payload.getActive());
        if (payload.getStock() != null && payload.getStock() >= 0) {
            ingredient.setStock(payload.getStock());
        } else if (payload.getStock() != null && payload.getStock() < 0) {
            throw new IllegalArgumentException("Tá»“n kho khÃ´ng Ä‘Æ°á»£c Ã¢m.");
        }
        return ingredientRepository.save(ingredient);
    }

    @Transactional
    public IngredientBatch receiveBatch(Long ingredientId, ReceiveIngredientBatchRequestDTO request, String actor) {
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Sá»‘ lÆ°á»£ng nháº­p pháº£i > 0.");
        }

        if (request.getProductionDate() != null && request.getExpiryDate() != null
                && request.getExpiryDate().isBefore(request.getProductionDate())) {
            throw new IllegalArgumentException("Háº¡n sá»­ dá»¥ng khÃ´ng Ä‘Æ°á»£c trÆ°á»›c ngÃ y sáº£n xuáº¥t.");
        }

        Ingredient ingredient = getIngredientEntity(ingredientId);
        if (!Boolean.TRUE.equals(ingredient.getActive())) {
            throw new IllegalStateException("NguyÃªn liá»‡u Ä‘ang ngÆ°ng hoáº¡t Ä‘á»™ng, khÃ´ng thá»ƒ nháº­p thÃªm lÃ´.");
        }
        double before = ingredient.getStock() == null ? 0.0 : ingredient.getStock();

        IngredientBatch batch = new IngredientBatch();
        batch.setIngredient(ingredient);
        batch.setQuantityReceived(request.getQuantity());
        batch.setQuantityRemaining(request.getQuantity());
        batch.setUnitCost(request.getUnitCost());
        batch.setSupplier(request.getSupplier());
        batch.setProductionDate(request.getProductionDate());
        batch.setExpiryDate(request.getExpiryDate());
        batch.setNote(request.getNote());

        ingredient.setStock(before + request.getQuantity());
        ingredientRepository.save(ingredient);

        recordIngredientMovement(
                ingredient,
                before,
                request.getQuantity(),
                before + request.getQuantity(),
                "IMPORT_BATCH",
                request.getSupplier(),
                request.getNote(),
                actor);

        return ingredientBatchRepository.save(batch);
    }

    @Transactional
    public void consumeIngredientByStaff(Long ingredientId, IngredientConsumeRequestDTO request, String actor) {
        if (request == null || request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Sá»‘ lÆ°á»£ng xuáº¥t dÃ¹ng pháº£i > 0.");
        }

        Ingredient ingredient = getIngredientEntity(ingredientId);
        consumeIngredientInternal(
                ingredient,
                request.getQuantity(),
                "CONSUME_PREP",
                request.getReason(),
                request.getNote(),
                actor);
    }

    @Transactional
    public void consumeIngredientsForSnack(Long snackId, int snackQuantity, String actor, String note) {
        if (snackQuantity <= 0) {
            throw new IllegalArgumentException("Sá»‘ lÆ°á»£ng snack pháº£i > 0.");
        }

        Snack snack = snackRepository.findById(snackId)
                .orElseThrow(() -> new IllegalArgumentException("Snack not found: " + snackId));

        List<SnackRecipeItem> recipeItems = snackRecipeItemRepository
                .findBySnack_SnackIdOrderByIngredient_IngredientNameAsc(snackId);
        if (recipeItems.isEmpty()) {
            throw new IllegalStateException("Snack chÆ°a cÃ³ cÃ´ng thá»©c nguyÃªn liá»‡u: " + snack.getSnackName());
        }

        for (SnackRecipeItem item : recipeItems) {
            if (item.getIngredient() == null || item.getQuantityPerSnack() == null) {
                continue;
            }
            double requiredQty = item.getQuantityPerSnack() * snackQuantity;
            if (requiredQty <= 0) {
                continue;
            }
            consumeIngredientInternal(
                    item.getIngredient(),
                    requiredQty,
                    "CONSUME_SNACK",
                    "Fulfill snack: " + snack.getSnackName(),
                    note,
                    actor);
        }
    }

    private boolean isBatchReceivedToday(IngredientBatch batch, LocalDate today) {
        return batch.getReceivedAt() != null && today.equals(batch.getReceivedAt().toLocalDate());
    }

    private boolean isBatchConsumable(IngredientBatch batch, boolean sameDayOnly, LocalDate today) {
        if (batch == null || isExpiredBatch(batch, today)) {
            return false;
        }
        if (sameDayOnly) {
            return isBatchReceivedToday(batch, today);
        }
        return true;
    }

    private boolean isSameDayOnlyIngredient(Ingredient ingredient) {
        String name = ingredient.getIngredientName();
        if (name == null) {
            return false;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("Ä‘Ã¡ viÃªn")
                || normalized.equals("da vien")
                || normalized.equals("nÆ°á»›c Ä‘Ã¡")
                || normalized.equals("nuoc da")
                || normalized.contains("Ä‘Ã¡ viÃªn")
                || normalized.contains("da vien")
                || normalized.contains("nÆ°á»›c Ä‘Ã¡")
                || normalized.contains("nuoc da");
    }

    public List<IngredientStockMovementDTO> getIngredientMovements(Long ingredientId) {
        return ingredientStockMovementRepository.findByIngredient_IngredientIdOrderByCreatedAtDesc(ingredientId).stream()
                .map(this::toMovementDTO)
                .collect(Collectors.toList());
    }

    public List<ShiftUsageItemDTO> getDailyIngredientUsage(LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        LocalDateTime from = target.atStartOfDay();
        LocalDateTime to = target.plusDays(1).atStartOfDay();

        Map<Long, ShiftUsageItemDTO> summary = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredientRepository.findAll()) {
            summary.put(
                ingredient.getIngredientId(),
                ShiftUsageItemDTO.builder()
                    .itemId(ingredient.getIngredientId())
                    .itemName(ingredient.getIngredientName())
                    .unit(ingredient.getUnit())
                    .importedQty(0.0)
                    .consumedQty(0.0)
                    .adjustedQty(0.0)
                    .closingStock(ingredient.getStock() == null ? 0.0 : ingredient.getStock())
                    .build());
        }

        List<IngredientStockMovement> movements = ingredientStockMovementRepository
                .findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);

        for (IngredientStockMovement movement : movements) {
            Long ingredientId = movement.getIngredient().getIngredientId();
            ShiftUsageItemDTO row = summary.get(ingredientId);
            if (row == null) {
                continue;
            }

            double change = movement.getQuantityChange() == null ? 0.0 : movement.getQuantityChange();
            String action = movement.getAction() == null ? "" : movement.getAction();

            boolean isConsumeAction = action.startsWith("CONSUME_");

            if ("IMPORT_BATCH".equals(action) && change > 0) {
                row.setImportedQty(row.getImportedQty() + change);
            } else if (isConsumeAction && change < 0) {
                row.setConsumedQty(row.getConsumedQty() + Math.abs(change));
            } else {
                row.setAdjustedQty(row.getAdjustedQty() + change);
            }
        }

        return new ArrayList<>(summary.values());
    }

    public List<IngredientBatchDTO> getBatchesByIngredient(Long ingredientId) {
        return ingredientBatchRepository.findByIngredient_IngredientIdOrderByReceivedAtDesc(ingredientId).stream()
                .map(this::toBatchDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void discardExpiredBatch(Long batchId, String actor) {
        IngredientBatch batch = ingredientBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        LocalDate today = LocalDate.now();
        if (!isExpiredBatch(batch, today)) {
            throw new IllegalArgumentException("LÃ´ nÃ y chÆ°a háº¿t háº¡n, khÃ´ng thá»ƒ loáº¡i bá».");
        }

        double discardQty = batch.getQuantityRemaining() == null ? 0.0 : batch.getQuantityRemaining();
        if (discardQty <= 0) {
            return;
        }

        Ingredient ingredient = batch.getIngredient();
        double before = ingredient.getStock() == null ? 0.0 : ingredient.getStock();
        double after = Math.max(0.0, before - discardQty);

        batch.setQuantityRemaining(0.0);
        ingredientBatchRepository.save(batch);

        ingredient.setStock(after);
        ingredientRepository.save(ingredient);

        recordIngredientMovement(
                ingredient,
                before,
                -discardQty,
                after,
                "DISCARD_EXPIRED",
                "Há»§y lÃ´ háº¿t háº¡n",
                "BatchId=" + batchId,
                actor);
    }

    @Transactional
    public int discardAllExpiredBatches(String actor) {
        LocalDate today = LocalDate.now();
        int discardedCount = 0;

        for (IngredientBatch batch : ingredientBatchRepository.findAll()) {
            if (!isExpiredBatch(batch, today)) {
                continue;
            }
            double remaining = batch.getQuantityRemaining() == null ? 0.0 : batch.getQuantityRemaining();
            if (remaining <= 0) {
                continue;
            }

            Ingredient ingredient = batch.getIngredient();
            double before = ingredient.getStock() == null ? 0.0 : ingredient.getStock();
            double after = Math.max(0.0, before - remaining);

            batch.setQuantityRemaining(0.0);
            ingredientBatchRepository.save(batch);

            ingredient.setStock(after);
            ingredientRepository.save(ingredient);

            recordIngredientMovement(
                    ingredient,
                    before,
                    -remaining,
                    after,
                    "DISCARD_EXPIRED",
                    "Há»§y lÃ´ háº¿t háº¡n",
                    "BatchId=" + batch.getBatchId(),
                    actor);

            discardedCount++;
        }

        return discardedCount;
    }

    public List<SnackRecipeItemDTO> getRecipeBySnack(Long snackId) {
        return snackRecipeItemRepository.findBySnack_SnackIdOrderByIngredient_IngredientNameAsc(snackId).stream()
                .map(this::toRecipeDTO)
                .collect(Collectors.toList());
    }

    public String getRecipeInstructions(Long snackId) {
        Snack snack = snackRepository.findById(snackId)
                .orElseThrow(() -> new IllegalArgumentException("Snack not found: " + snackId));
        return snack.getRecipeInstructions();
    }

    @Transactional
    public Snack updateRecipeInstructions(Long snackId, String instructions) {
        Snack snack = snackRepository.findById(snackId)
                .orElseThrow(() -> new IllegalArgumentException("Snack not found: " + snackId));
        snack.setRecipeInstructions(instructions);
        return snackRepository.save(snack);
    }

    @Transactional
    public List<SnackRecipeItemDTO> replaceRecipe(Long snackId, List<SnackRecipeItemRequestDTO> requests) {
        Snack snack = snackRepository.findById(snackId)
                .orElseThrow(() -> new IllegalArgumentException("Snack not found: " + snackId));

        Map<Long, Double> groupedRequests = new LinkedHashMap<>();
        if (requests != null) {
            for (SnackRecipeItemRequestDTO request : requests) {
                if (request == null || request.getIngredientId() == null || request.getQuantityPerSnack() == null
                        || request.getQuantityPerSnack() <= 0) {
                    throw new IllegalArgumentException("Cong thuc khong hop le: ingredientId va quantityPerSnack phai > 0.");
                }
                groupedRequests.merge(request.getIngredientId(), request.getQuantityPerSnack(), Double::sum);
            }
        }

        snackRecipeItemRepository.deleteBySnack_SnackId(snackId);
        snackRecipeItemRepository.flush();

        List<SnackRecipeItem> createdItems = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : groupedRequests.entrySet()) {
            Ingredient ingredient = getIngredientEntity(entry.getKey());
            SnackRecipeItem item = new SnackRecipeItem();
            item.setSnack(snack);
            item.setIngredient(ingredient);
            item.setQuantityPerSnack(entry.getValue());
            createdItems.add(snackRecipeItemRepository.save(item));
        }

        return createdItems.stream().map(this::toRecipeDTO).collect(Collectors.toList());
    }

    private Ingredient getIngredientEntity(Long ingredientId) {
        return ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("Ingredient not found: " + ingredientId));
    }

    private IngredientDTO toIngredientDTO(Ingredient ingredient, Double displayStock) {
        return IngredientDTO.builder()
                .ingredientId(ingredient.getIngredientId())
                .ingredientName(ingredient.getIngredientName())
                .unit(ingredient.getUnit())
                .stock(displayStock)
                .active(ingredient.getActive())
                .build();
    }

    private double calculateUsableStock(Ingredient ingredient, LocalDate today) {
        List<IngredientBatch> batches = ingredientBatchRepository
                .findByIngredient_IngredientIdOrderByReceivedAtDesc(ingredient.getIngredientId());

        if (batches.isEmpty()) {
            return ingredient.getStock() == null ? 0.0 : ingredient.getStock();
        }

        return batches.stream()
                .filter(batch -> {
                    LocalDate effectiveExpiryDate = getEffectiveExpiryDate(batch);
                    return effectiveExpiryDate == null || !effectiveExpiryDate.isBefore(today);
                })
                .mapToDouble(batch -> batch.getQuantityRemaining() == null ? 0.0 : batch.getQuantityRemaining())
                .sum();
    }

    private IngredientBatchDTO toBatchDTO(IngredientBatch batch) {
        LocalDate effectiveExpiryDate = getEffectiveExpiryDate(batch);
        return IngredientBatchDTO.builder()
                .batchId(batch.getBatchId())
                .ingredientId(batch.getIngredient().getIngredientId())
                .ingredientName(batch.getIngredient().getIngredientName())
                .quantityReceived(batch.getQuantityReceived())
                .quantityRemaining(batch.getQuantityRemaining())
                .unitCost(batch.getUnitCost())
                .supplier(batch.getSupplier())
                .productionDate(batch.getProductionDate())
                .expiryDate(effectiveExpiryDate)
                .receivedAt(batch.getReceivedAt())
                .note(batch.getNote())
                .build();
    }

    private LocalDate getEffectiveExpiryDate(IngredientBatch batch) {
        if (batch == null) {
            return null;
        }
        if (batch.getIngredient() != null && isSameDayOnlyIngredient(batch.getIngredient())) {
            return batch.getReceivedAt() == null ? null : batch.getReceivedAt().toLocalDate();
        }
        return batch.getExpiryDate();
    }

    private boolean isExpiredBatch(IngredientBatch batch, LocalDate today) {
        LocalDate effectiveExpiryDate = getEffectiveExpiryDate(batch);
        return effectiveExpiryDate != null && effectiveExpiryDate.isBefore(today);
    }

    private SnackRecipeItemDTO toRecipeDTO(SnackRecipeItem item) {
        return SnackRecipeItemDTO.builder()
                .recipeItemId(item.getRecipeItemId())
                .snackId(item.getSnack().getSnackId())
                .snackName(item.getSnack().getSnackName())
                .ingredientId(item.getIngredient().getIngredientId())
                .ingredientName(item.getIngredient().getIngredientName())
                .unit(item.getIngredient().getUnit())
                .quantityPerSnack(item.getQuantityPerSnack())
                .build();
    }

    private IngredientStockMovementDTO toMovementDTO(IngredientStockMovement movement) {
        String display = mapActionToVietnamese(movement.getAction());
        IngredientStockMovementDTO dto = IngredientStockMovementDTO.builder()
                .movementId(movement.getMovementId())
                .ingredientId(movement.getIngredient().getIngredientId())
                .ingredientName(movement.getIngredient().getIngredientName())
                .quantityBefore(movement.getQuantityBefore())
                .quantityChange(movement.getQuantityChange())
                .quantityAfter(movement.getQuantityAfter())
                .action(movement.getAction())
                .reason(movement.getReason())
                .note(movement.getNote())
                .performedBy(movement.getPerformedBy())
                .createdAt(movement.getCreatedAt())
                .actionDisplay(display)
                .build();
        return dto;
    }

    private static final Map<String, String> ACTION_LABELS = Map.of(
            "IMPORT_BATCH", "Nháº­p lÃ´",
            "CONSUME_PREP", "Xuáº¥t dÃ¹ng",
            "CONSUME_SNACK", "Xuáº¥t cho báº¯p nÆ°á»›c",
            "CONSUME_PRODUCTION", "Xuáº¥t cháº¿ biáº¿n",
            "DISCARD_EXPIRED", "Há»§y lÃ´ háº¿t háº¡n",
            "ADJUSTMENT", "Äiá»u chá»‰nh"
    );

    private void consumeIngredientInternal(
            Ingredient ingredient,
            Double quantity,
            String action,
            String reason,
            String note,
            String actor) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Sá»‘ lÆ°á»£ng xuáº¥t dÃ¹ng pháº£i > 0.");
        }
        if (!Boolean.TRUE.equals(ingredient.getActive())) {
            throw new IllegalStateException("NguyÃªn liá»‡u Ä‘ang ngÆ°ng hoáº¡t Ä‘á»™ng, khÃ´ng thá»ƒ xuáº¥t dÃ¹ng.");
        }

        final boolean sameDayOnly = isSameDayOnlyIngredient(ingredient);
        final LocalDate today = LocalDate.now();
        Long ingredientId = ingredient.getIngredientId();

        double before = ingredient.getStock() == null ? 0.0 : ingredient.getStock();
        List<IngredientBatch> batches = ingredientBatchRepository
                .findByIngredient_IngredientIdOrderByReceivedAtDesc(ingredientId);
        double availableForConsumption = batches.stream()
                .filter(batch -> isBatchConsumable(batch, sameDayOnly, today))
                .mapToDouble(batch -> batch.getQuantityRemaining() == null ? 0.0 : batch.getQuantityRemaining())
                .sum();

        if (availableForConsumption < quantity) {
            if (sameDayOnly) {
                throw new IllegalStateException(
                        "ÄÃ¡ viÃªn chá»‰ Ä‘Æ°á»£c dÃ¹ng trong ngÃ y nháº­p. LÃ´ Ä‘Ã¡ hÃ´m nay khÃ´ng Ä‘á»§, vui lÃ²ng nháº­p Ä‘Ã¡ viÃªn má»›i.");
            }
            throw new IllegalStateException("KhÃ´ng Ä‘á»§ tá»“n kho nguyÃªn liá»‡u Ä‘á»ƒ xuáº¥t dÃ¹ng.");
        }

        double remainingNeed = quantity;
        for (IngredientBatch batch : batches) {
            if (remainingNeed <= 0) {
                break;
            }
            if (!isBatchConsumable(batch, sameDayOnly, today)) {
                continue;
            }
            double available = batch.getQuantityRemaining() == null ? 0.0 : batch.getQuantityRemaining();
            if (available <= 0) {
                continue;
            }

            double consumed = Math.min(available, remainingNeed);
            batch.setQuantityRemaining(available - consumed);
            ingredientBatchRepository.save(batch);
            remainingNeed -= consumed;
        }

        if (remainingNeed > 0) {
            throw new IllegalStateException("Dá»¯ liá»‡u lÃ´ khÃ´ng Ä‘á»§ Ä‘á»ƒ trá»« kho. Vui lÃ²ng kiá»ƒm tra láº¡i cÃ¡c lÃ´ nguyÃªn liá»‡u.");
        }

        double after = before - quantity;
        ingredient.setStock(after);
        ingredientRepository.save(ingredient);

        recordIngredientMovement(
                ingredient,
                before,
                -quantity,
                after,
                action,
                reason,
                note,
                actor);
    }

    private String mapActionToVietnamese(String action) {
        if (action == null) return null;
        return ACTION_LABELS.getOrDefault(action, action);
    }

    private void recordIngredientMovement(
            Ingredient ingredient,
            Double quantityBefore,
            Double quantityChange,
            Double quantityAfter,
            String action,
            String reason,
            String note,
            String actor) {
        IngredientStockMovement movement = new IngredientStockMovement();
        movement.setIngredient(ingredient);
        movement.setQuantityBefore(quantityBefore);
        movement.setQuantityChange(quantityChange);
        movement.setQuantityAfter(quantityAfter);
        movement.setAction(action);
        movement.setReason(reason);
        movement.setNote(note);
        movement.setPerformedBy(actor != null && !actor.isBlank() ? actor : "system");
        ingredientStockMovementRepository.save(movement);
    }
}

