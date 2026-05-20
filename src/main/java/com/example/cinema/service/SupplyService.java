package com.example.cinema.service;

import com.example.cinema.domain.SupplyItem;
import com.example.cinema.domain.SupplyStockMovement;
import com.example.cinema.dto.AdjustSupplyStockRequestDTO;
import com.example.cinema.dto.ShiftUsageItemDTO;
import com.example.cinema.dto.SupplyItemDTO;
import com.example.cinema.repository.SupplyItemRepository;
import com.example.cinema.repository.SupplyStockMovementRepository;
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
public class SupplyService {

    private final SupplyItemRepository supplyItemRepository;
    private final SupplyStockMovementRepository supplyStockMovementRepository;

    public List<SupplyItemDTO> getSupplies() {
        return supplyItemRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<SupplyItemDTO> getLowStockSupplies() {
        return supplyItemRepository.findAll().stream()
                .filter(item -> item.getStock() != null
                        && item.getReorderLevel() != null
                        && item.getStock() <= item.getReorderLevel())
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSupplyMovements(Long supplyId) {
        SupplyItem supply = supplyItemRepository.findById(supplyId)
                .orElseThrow(() -> new IllegalArgumentException("Supply not found: " + supplyId));

        List<Map<String, Object>> rows = supplyStockMovementRepository
                .findTop50BySupply_SupplyIdOrderByCreatedAtDesc(supplyId)
                .stream()
                .map(movement -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("movementId", movement.getMovementId());
                    row.put("supplyId", movement.getSupply().getSupplyId());
                    row.put("supplyName", movement.getSupply().getSupplyName());
                    row.put("quantityBefore", movement.getQuantityBefore());
                    row.put("quantityChange", movement.getQuantityChange());
                    row.put("quantityAfter", movement.getQuantityAfter());
                    row.put("action", movement.getAction());
                    row.put("note", movement.getNote());
                    row.put("performedBy", movement.getPerformedBy());
                    row.put("createdAt", movement.getCreatedAt());
                    return row;
                })
                .collect(Collectors.toList());

        double currentStock = supply.getStock() == null ? 0.0 : supply.getStock();
        if (rows.isEmpty() && currentStock > 0) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("movementId", -supply.getSupplyId());
            row.put("supplyId", supply.getSupplyId());
            row.put("supplyName", supply.getSupplyName());
            row.put("quantityBefore", 0.0);
            row.put("quantityChange", currentStock);
            row.put("quantityAfter", currentStock);
            row.put("action", "CURRENT_STOCK");
            row.put("note", "Vật tư đang có tồn kho nhưng chưa có lịch sử nhập/xuất trước đó.");
            row.put("performedBy", "system");
            row.put("createdAt", null);
            rows.add(row);
        }
        return rows;
    }

    @Transactional
    public SupplyItemDTO createSupply(SupplyItem payload) {
        payload.setSupplyId(null);
        if (payload.getStock() == null) {
            payload.setStock(0.0);
        }
        if (payload.getReorderLevel() == null) {
            payload.setReorderLevel(10.0);
        }
        if (payload.getActive() == null) {
            payload.setActive(true);
        }
        return toDTO(supplyItemRepository.save(payload));
    }

    @Transactional
    public SupplyItemDTO updateSupply(Long supplyId, SupplyItem payload) {
        SupplyItem current = supplyItemRepository.findById(supplyId)
                .orElseThrow(() -> new IllegalArgumentException("Supply not found: " + supplyId));

        current.setSupplyName(payload.getSupplyName());
        current.setUnit(payload.getUnit());
        current.setActive(payload.getActive());
        if (payload.getReorderLevel() != null && payload.getReorderLevel() >= 0) {
            current.setReorderLevel(payload.getReorderLevel());
        }
        if (payload.getStock() != null && payload.getStock() >= 0) {
            current.setStock(payload.getStock());
        }

        return toDTO(supplyItemRepository.save(current));
    }

    @Transactional
    public void deleteSupply(Long supplyId) {
        if (!supplyItemRepository.existsById(supplyId)) {
            throw new IllegalArgumentException("Supply not found: " + supplyId);
        }
        supplyStockMovementRepository.deleteBySupply_SupplyId(supplyId);
        supplyItemRepository.deleteById(supplyId);
    }

    @Transactional
    public SupplyItemDTO adjustSupplyStock(Long supplyId, AdjustSupplyStockRequestDTO request, String actor) {
        if (request == null || request.getQuantity() == null || request.getQuantity() < 0) {
            throw new IllegalArgumentException("quantity phải >= 0");
        }

        SupplyItem current = supplyItemRepository.findById(supplyId)
                .orElseThrow(() -> new IllegalArgumentException("Supply not found: " + supplyId));

        String operation = request.getOperation() == null
                ? "SET"
                : request.getOperation().trim().toUpperCase(Locale.ROOT);

        double before = current.getStock() == null ? 0.0 : current.getStock();
        double after;
        switch (operation) {
            case "ADD":
                after = before + request.getQuantity();
                break;
            case "SUBTRACT":
                after = before - request.getQuantity();
                if (after < 0) {
                    throw new IllegalArgumentException("Không thể trừ vượt quá tồn kho vật tư hiện tại.");
                }
                break;
            case "SET":
                after = request.getQuantity();
                break;
            default:
                throw new IllegalArgumentException("operation chỉ hỗ trợ: SET, ADD, SUBTRACT");
        }

        current.setStock(after);
        supplyItemRepository.save(current);

        recordSupplyMovement(
                current,
                before,
                after - before,
                after,
                operation,
                request.getNote(),
                actor);

        return toDTO(current);
    }

    public List<ShiftUsageItemDTO> getDailySupplyUsage(LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        LocalDateTime from = target.atStartOfDay();
        LocalDateTime to = target.plusDays(1).atStartOfDay();

        Map<Long, ShiftUsageItemDTO> summary = new LinkedHashMap<>();
        for (SupplyItem item : supplyItemRepository.findAll()) {
                summary.put(
                    item.getSupplyId(),
                    ShiftUsageItemDTO.builder()
                        .itemId(item.getSupplyId())
                        .itemName(item.getSupplyName())
                        .unit(item.getUnit())
                        .importedQty(0.0)
                        .consumedQty(0.0)
                        .adjustedQty(0.0)
                        .closingStock(item.getStock() == null ? 0.0 : item.getStock())
                        .build());
        }

        List<SupplyStockMovement> movements = supplyStockMovementRepository
                .findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);

        for (SupplyStockMovement movement : movements) {
            Long id = movement.getSupply().getSupplyId();
            ShiftUsageItemDTO row = summary.get(id);
            if (row == null) {
                continue;
            }

            double change = movement.getQuantityChange() == null ? 0.0 : movement.getQuantityChange();
            String action = movement.getAction() == null ? "" : movement.getAction();
            if ("ADD".equals(action) && change > 0) {
                row.setImportedQty(row.getImportedQty() + change);
            } else if ("SUBTRACT".equals(action) && change < 0) {
                row.setConsumedQty(row.getConsumedQty() + Math.abs(change));
            } else {
                row.setAdjustedQty(row.getAdjustedQty() + change);
            }
        }

        return new ArrayList<>(summary.values());
    }

    private void recordSupplyMovement(
            SupplyItem item,
            Double quantityBefore,
            Double quantityChange,
            Double quantityAfter,
            String action,
            String note,
            String actor) {
        SupplyStockMovement movement = new SupplyStockMovement();
        movement.setSupply(item);
        movement.setQuantityBefore(quantityBefore);
        movement.setQuantityChange(quantityChange);
        movement.setQuantityAfter(quantityAfter);
        movement.setAction(action);
        movement.setNote(note);
        movement.setPerformedBy(actor != null && !actor.isBlank() ? actor : "system");
        supplyStockMovementRepository.save(movement);
    }

    private SupplyItemDTO toDTO(SupplyItem item) {
        return SupplyItemDTO.builder()
            .supplyId(item.getSupplyId())
            .supplyName(item.getSupplyName())
            .unit(item.getUnit())
            .stock(item.getStock())
            .reorderLevel(item.getReorderLevel())
            .active(item.getActive())
            .build();
    }
}
