package com.example.cinema.service;

import com.example.cinema.domain.Booking;
import com.example.cinema.domain.BookingSnack;
import com.example.cinema.domain.Snack;
import com.example.cinema.domain.SnackWarehouseMovement;
import com.example.cinema.dto.AddSnacksRequestDTO;
import com.example.cinema.dto.BookingSnackDTO;
import com.example.cinema.dto.ShiftUsageItemDTO;
import com.example.cinema.dto.SnackDTO;
import com.example.cinema.dto.SnackItemRequestDTO;
import com.example.cinema.dto.SnackWarehouseDTO;
import com.example.cinema.dto.UpdateSnackWarehouseStockRequestDTO;
import com.example.cinema.repository.BookingRepository;
import com.example.cinema.repository.BookingSnackRepository;
import com.example.cinema.repository.SnackRepository;
import com.example.cinema.repository.SnackWarehouseMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SnackService {

    private final SnackRepository snackRepository;
    private final BookingSnackRepository bookingSnackRepository;
    private final BookingRepository bookingRepository;
    private final SnackWarehouseMovementRepository snackWarehouseMovementRepository;
    private final InventoryService inventoryService;

    /**
     * Lấy tất cả snacks available
     */
    public List<SnackDTO> getAllAvailableSnacks() {
        return snackRepository.findByAvailableTrue().stream()
                .filter(snack -> !isExpiredSnack(snack))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Lấy snacks theo category
     */
    public List<SnackDTO> getSnacksByCategory(Snack.SnackCategory category) {
        return snackRepository.findByCategoryAndAvailableTrue(category).stream()
                .filter(snack -> !isExpiredSnack(snack))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<SnackDTO> getAllSnacksForAdmin() {
        return snackRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<SnackDTO> getExpiringSoonSnacks(Integer days) {
        int safeDays = days == null ? 7 : Math.max(0, days);
        LocalDate dueDate = LocalDate.now().plusDays(safeDays);
        return snackRepository.findByExpiryDateLessThanEqual(dueDate).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<SnackWarehouseDTO> getSnackWarehouseStocks() {
        return snackRepository.findAll().stream()
                .filter(this::isWarehouseTrackable)
                .map(this::convertToWarehouseDTO)
                .collect(Collectors.toList());
    }

    public List<SnackWarehouseDTO> getLowWarehouseStocks(Double threshold) {
        final double safeThreshold = threshold == null ? 0.0 : Math.max(0.0, threshold);
        return snackRepository.findAll().stream()
                .filter(this::isWarehouseTrackable)
                .filter(snack -> {
                    double compareThreshold = safeThreshold > 0
                            ? safeThreshold
                            : (snack.getWarehouseReorderLevel() == null ? 0.0 : snack.getWarehouseReorderLevel());
                    return snack.getWarehouseStock() != null && snack.getWarehouseStock() <= compareThreshold;
                })
                .map(this::convertToWarehouseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Lấy snack theo ID
     */
    public Snack getSnackById(Long snackId) {
        return snackRepository.findById(snackId)
                .orElseThrow(() -> new RuntimeException("Snack not found with id: " + snackId));
    }

    /**
     * Tạo snack mới (Admin)
     */
    @Transactional
    public Snack createSnack(Snack snack) {
        Snack created = snackRepository.save(snack);
        double initialStock = created.getWarehouseStock() == null ? 0.0 : created.getWarehouseStock();
        if (isWarehouseTrackable(created) && initialStock > 0) {
            SnackWarehouseMovement movement = new SnackWarehouseMovement();
            movement.setSnack(created);
            movement.setQuantityBefore(0.0);
            movement.setQuantityChange(initialStock);
            movement.setQuantityAfter(initialStock);
            movement.setAction("CREATE_INITIAL_STOCK");
            movement.setNote("Tạo sản phẩm với tồn ban đầu");
            movement.setPerformedBy("admin");
            snackWarehouseMovementRepository.save(movement);
        }
        return created;
    }

    /**
     * Cập nhật snack (Admin)
     */
    @Transactional
    public Snack updateSnack(Long snackId, Snack snackDetails) {
        Snack snack = getSnackById(snackId);
        snack.setSnackName(snackDetails.getSnackName());
        snack.setDescription(snackDetails.getDescription());
        snack.setPrice(snackDetails.getPrice());
        snack.setImageUrl(snackDetails.getImageUrl());
        snack.setCategory(snackDetails.getCategory());
        snack.setAvailable(snackDetails.getAvailable());
        snack.setExpiryDate(snackDetails.getExpiryDate());
        if (snackDetails.getWarehouseTrackable() != null) {
            snack.setWarehouseTrackable(snackDetails.getWarehouseTrackable());
        }
        if (snackDetails.getWarehouseStock() != null && snackDetails.getWarehouseStock() >= 0) {
            snack.setWarehouseStock(snackDetails.getWarehouseStock());
        }
        if (snackDetails.getWarehouseReorderLevel() != null && snackDetails.getWarehouseReorderLevel() >= 0) {
            snack.setWarehouseReorderLevel(snackDetails.getWarehouseReorderLevel());
        }
        return snackRepository.save(snack);
    }

    @Transactional
    public SnackWarehouseDTO updateWarehouseStock(Long snackId, UpdateSnackWarehouseStockRequestDTO request, String actor) {
        if (request == null || (request.getQuantity() == null && request.getReorderLevel() == null)) {
            throw new IllegalArgumentException("Cần nhập số lượng hoặc mức cảnh báo.");
        }
        if (request.getQuantity() != null && request.getQuantity() < 0) {
            throw new IllegalArgumentException("quantity phải >= 0");
        }
        if (request.getReorderLevel() != null && request.getReorderLevel() < 0) {
            throw new IllegalArgumentException("Mức cảnh báo phải >= 0");
        }

        Snack snack = getSnackById(snackId);
        if (!isWarehouseTrackable(snack)) {
            throw new IllegalArgumentException("Snack này không theo dõi tồn kho thành phẩm.");
        }
        String op = request.getOperation() == null ? "SET" : request.getOperation().trim().toUpperCase(Locale.ROOT);
        double current = snack.getWarehouseStock() == null ? 0.0 : snack.getWarehouseStock();
        double next = current;
        boolean hasQuantityChange = request.getQuantity() != null;

        if (hasQuantityChange) {
            switch (op) {
                case "ADD":
                    next = current + request.getQuantity();
                    break;
                case "SUBTRACT":
                    next = current - request.getQuantity();
                    if (next < 0) {
                        throw new IllegalArgumentException("Không thể trừ vượt quá tồn kho hiện tại.");
                    }
                    break;
                case "SET":
                    next = request.getQuantity();
                    break;
                default:
                    throw new IllegalArgumentException("operation chỉ hỗ trợ: SET, ADD, SUBTRACT");
            }
            snack.setWarehouseStock(next);
        }

        if (request.getReorderLevel() != null) {
            snack.setWarehouseReorderLevel(request.getReorderLevel());
        }

        snackRepository.save(snack);

        SnackWarehouseMovement movement = new SnackWarehouseMovement();
        movement.setSnack(snack);
        movement.setQuantityBefore(current);
        movement.setQuantityChange(next - current);
        movement.setQuantityAfter(next);
        movement.setAction(hasQuantityChange ? op : "UPDATE_REORDER_LEVEL");
        movement.setNote(request.getNote());
        movement.setPerformedBy(actor != null && !actor.isBlank() ? actor : "admin");
        snackWarehouseMovementRepository.save(movement);

        return convertToWarehouseDTO(snack);
    }

    public List<ShiftUsageItemDTO> getDailySnackWarehouseUsage(LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        LocalDateTime from = target.atStartOfDay();
        LocalDateTime to = target.plusDays(1).atStartOfDay();

        Map<Long, ShiftUsageItemDTO> summary = new LinkedHashMap<>();
        for (Snack snack : snackRepository.findAll()) {
            if (!isWarehouseTrackable(snack)) {
                continue;
            }
                summary.put(
                    snack.getSnackId(),
                    ShiftUsageItemDTO.builder()
                        .itemId(snack.getSnackId())
                        .itemName(snack.getSnackName())
                        .unit("phần")
                        .importedQty(0.0)
                        .consumedQty(0.0)
                        .adjustedQty(0.0)
                        .closingStock(snack.getWarehouseStock() == null ? 0.0 : snack.getWarehouseStock())
                        .build());
        }

        List<SnackWarehouseMovement> movements = snackWarehouseMovementRepository
                .findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);

        for (SnackWarehouseMovement movement : movements) {
            Long id = movement.getSnack().getSnackId();
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

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSnackWarehouseMovements(Long snackId) {
        Snack snack = getSnackById(snackId);
        List<Map<String, Object>> rows = snackWarehouseMovementRepository.findTop50BySnack_SnackIdOrderByCreatedAtDesc(snackId)
                .stream()
                .map(movement -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("movementId", movement.getMovementId());
                    row.put("snackId", movement.getSnack().getSnackId());
                    row.put("snackName", movement.getSnack().getSnackName());
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
        double currentStock = snack.getWarehouseStock() == null ? 0.0 : snack.getWarehouseStock();
        if (rows.isEmpty() && isWarehouseTrackable(snack) && currentStock > 0) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("movementId", -snack.getSnackId());
            row.put("snackId", snack.getSnackId());
            row.put("snackName", snack.getSnackName());
            row.put("quantityBefore", 0.0);
            row.put("quantityChange", currentStock);
            row.put("quantityAfter", currentStock);
            row.put("action", "CURRENT_STOCK");
            row.put("note", "Sản phẩm đang có tồn kho nhưng chưa có lịch sử nhập/xuất trước đó.");
            row.put("performedBy", "system");
            row.put("createdAt", null);
            rows.add(row);
        }
        return rows;
    }

    /**
     * Xóa snack (Admin)
     */
    @Transactional
    public void deleteSnack(Long snackId) {
        snackRepository.deleteById(snackId);
    }

    /**
     * Thêm snacks vào các bookings của một transaction
     * Sử dụng khi customer đã chọn snacks và cần lưu vào database
     */
    @Transactional
    public Map<String, Object> addSnacksToBookings(AddSnacksRequestDTO request) {
        String txnRef = request.getTxnRef();
        List<SnackItemRequestDTO> snackItems = request.getSnacks();

        // Tìm tất cả bookings trong transaction này
        List<Booking> bookings = bookingRepository.findByTxnRef(txnRef);
        if (bookings.isEmpty()) {
            throw new RuntimeException("No bookings found with txnRef: " + txnRef);
        }

        // Tính tổng tiền snacks
        double totalSnacksCost = 0.0;

        // Thêm snacks vào từng booking (hoặc chỉ booking đầu tiên nếu muốn group)
        // Ở đây tôi thêm vào booking đầu tiên trong txnRef
        Booking firstBooking = bookings.get(0);

        for (SnackItemRequestDTO item : snackItems) {
            if (item.getQuantity() <= 0) {
                continue;
            }

            Snack snack = getSnackById(item.getSnackId());

            if (snack.getExpiryDate() != null && snack.getExpiryDate().isBefore(LocalDate.now())) {
                throw new RuntimeException("Snack đã quá hạn sử dụng: " + snack.getSnackName());
            }

            // Online booking chỉ ghi nhận món đã chọn.
            // Việc kiểm tra/trừ nguyên liệu sẽ xử lý ở bước staff fulfill tại quầy.

            // Tạo BookingSnack
            BookingSnack bookingSnack = new BookingSnack();
            bookingSnack.setBooking(firstBooking);
            bookingSnack.setSnack(snack);
            bookingSnack.setQuantity(item.getQuantity());
            bookingSnack.setPriceAtPurchase(snack.getPrice());

            bookingSnackRepository.save(bookingSnack);

            totalSnacksCost += bookingSnack.getSubtotal();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("txnRef", txnRef);
        response.put("totalSnacksCost", totalSnacksCost);
        response.put("message", "Snacks added successfully");

        return response;
    }

    /**
     * Lấy danh sách snacks của một booking
     */
    public List<BookingSnackDTO> getBookingSnacks(Long bookingId) {
        return bookingSnackRepository.findByBooking_BookingId(bookingId).stream()
                .map(this::convertToBookingSnackDTO)
                .collect(Collectors.toList());
    }

    /**
     * Lấy danh sách snacks của một transaction
     */
    public List<BookingSnackDTO> getSnacksByTxnRef(String txnRef) {
        return bookingSnackRepository.findByTxnRef(txnRef).stream()
                .map(this::convertToBookingSnackDTO)
                .collect(Collectors.toList());
    }

    /**
     * Tính tổng tiền snacks của một transaction
     */
    public Double calculateSnacksTotalByTxnRef(String txnRef) {
        return bookingSnackRepository.findByTxnRef(txnRef).stream()
                .mapToDouble(BookingSnack::getSubtotal)
                .sum();
    }

    @Transactional
    public Map<String, Object> fulfillSnacksByTxn(String txnRef, String actor) {
        if (txnRef == null || txnRef.isBlank()) {
            throw new IllegalArgumentException("Mã giao dịch không hợp lệ.");
        }

        List<Booking> bookings = bookingRepository.findByTxnRef(txnRef);
        if (bookings.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy vé với mã giao dịch: " + txnRef);
        }

        boolean alreadyFulfilled = bookings.stream().allMatch(Booking::isSnacksFulfilled);
        if (alreadyFulfilled) {
            throw new IllegalStateException("Bắp nước đã được xuất kho cho mã giao dịch này.");
        }

        for (Booking booking : bookings) {
            if (booking.getStatus() != Booking.Status.PAID) {
                throw new IllegalStateException("Vé chưa thanh toán, không thể xuất bắp nước.");
            }
        }

        List<BookingSnack> items = bookingSnackRepository.findByTxnRef(txnRef);
        if (items.isEmpty()) {
            bookings.forEach(b -> b.setSnacksFulfilled(true));
            bookingRepository.saveAll(bookings);
            return Map.of(
                    "success", true,
                    "txnRef", txnRef,
                    "message", "Không có bắp nước để xuất.");
        }

        Map<Snack, Integer> expandedItems = new LinkedHashMap<>();
        for (BookingSnack item : items) {
            if (item.getSnack() == null) {
                continue;
            }
            int qty = item.getQuantity() == null ? 0 : item.getQuantity();
            if (qty <= 0) {
                continue;
            }

            Snack snack = item.getSnack();
            if (snack.getCategory() == Snack.SnackCategory.COMBO) {
                Map<Snack, Integer> components = expandComboItems(snack, qty);
                for (Map.Entry<Snack, Integer> entry : components.entrySet()) {
                    expandedItems.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
            } else {
                expandedItems.merge(snack, qty, Integer::sum);
            }
        }

        Map<String, Integer> summary = new LinkedHashMap<>();
        for (Map.Entry<Snack, Integer> entry : expandedItems.entrySet()) {
            Snack snack = entry.getKey();
            int qty = entry.getValue();
            if (qty <= 0) {
                continue;
            }
            summary.merge(snack.getSnackName(), qty, Integer::sum);

            if (isWarehouseTrackable(snack)) {
                double before = snack.getWarehouseStock() == null ? 0.0 : snack.getWarehouseStock();
                double after = before - qty;
                if (after < 0) {
                    throw new IllegalStateException(
                            "Tồn kho thành phẩm không đủ cho " + snack.getSnackName());
                }
                snack.setWarehouseStock(after);
                snackRepository.save(snack);

                SnackWarehouseMovement movement = new SnackWarehouseMovement();
                movement.setSnack(snack);
                movement.setQuantityBefore(before);
                movement.setQuantityChange(after - before);
                movement.setQuantityAfter(after);
                movement.setAction("SUBTRACT");
                movement.setNote("Fulfill txnRef=" + txnRef);
                movement.setPerformedBy(actor != null && !actor.isBlank() ? actor : "staff");
                snackWarehouseMovementRepository.save(movement);
            } else {
                inventoryService.consumeIngredientsForSnack(
                        snack.getSnackId(),
                        qty,
                        actor,
                        "txnRef=" + txnRef);
            }
        }

        bookings.forEach(b -> b.setSnacksFulfilled(true));
        bookingRepository.saveAll(bookings);

        return Map.of(
                "success", true,
                "txnRef", txnRef,
                "items", summary,
                "message", "Đã xuất bắp nước thành công.");
    }

    private Map<Snack, Integer> expandComboItems(Snack combo, int comboQty) {
        String comboName = combo.getSnackName() == null ? "" : combo.getSnackName().trim();
        Map<Snack, Integer> result = new LinkedHashMap<>();

        if (comboQty <= 0) {
            return result;
        }

        if (equalsIgnoreCase(comboName, "Combo Gấu")) {
            result.put(findSnackByName("Coke 32oz"), 1 * comboQty);
            result.put(findSnackByAnyName("Bắp riêng", "Bắp 2 Ngăn 64OZ Phô Mai + Caramel"), 1 * comboQty);
            return result;
        }

        if (equalsIgnoreCase(comboName, "Combo Có Gấu")) {
            result.put(findSnackByName("Coke 32oz"), 2 * comboQty);
            result.put(findSnackByAnyName("Bắp riêng", "Bắp 2 Ngăn 64OZ Phô Mai + Caramel"), 1 * comboQty);
            return result;
        }

        if (equalsIgnoreCase(comboName, "Combo Nhà Gấu")) {
            result.put(findSnackByName("Coke 32oz"), 4 * comboQty);
            result.put(findSnackByAnyName("Bắp riêng", "Bắp 2 Ngăn 64OZ Phô Mai + Caramel"), 2 * comboQty);
            return result;
        }

        throw new IllegalStateException("Chưa cấu hình tách combo cho: " + comboName);
    }

    private Snack findSnackByName(String snackName) {
        return snackRepository.findBySnackNameIgnoreCase(snackName)
                .orElseThrow(() -> new IllegalStateException(
                        "Thiếu snack: " + snackName + ". Vui lòng tạo snack này trước."));
    }

    private Snack findSnackByAnyName(String... snackNames) {
        for (String snackName : snackNames) {
            Optional<Snack> snack = snackRepository.findBySnackNameIgnoreCase(snackName);
            if (snack.isPresent()) {
                return snack.get();
            }
        }
        throw new IllegalStateException("Thiếu snack: " + String.join(" hoặc ", snackNames) + ". Vui lòng tạo snack này trước.");
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    // Helper methods
    private SnackDTO convertToDTO(Snack snack) {
        return SnackDTO.builder()
            .snackId(snack.getSnackId())
            .snackName(snack.getSnackName())
            .description(snack.getDescription())
            .price(snack.getPrice())
            .imageUrl(snack.getImageUrl())
            .category(snack.getCategory().name())
            .available(snack.getAvailable())
            .warehouseTrackable(snack.getWarehouseTrackable())
            .expiryDate(snack.getExpiryDate())
            .recipeInstructions(snack.getRecipeInstructions())
            .build();
    }

    // Public helpers to return DTOs for controllers
    public SnackDTO getSnackDTOById(Long snackId) {
        return convertToDTO(getSnackById(snackId));
    }

    public SnackDTO createSnackAndReturnDTO(Snack snack) {
        Snack created = createSnack(snack);
        return convertToDTO(created);
    }

    public SnackDTO updateSnackAndReturnDTO(Long snackId, Snack snackDetails) {
        Snack updated = updateSnack(snackId, snackDetails);
        return convertToDTO(updated);
    }

    private BookingSnackDTO convertToBookingSnackDTO(BookingSnack bs) {
        return BookingSnackDTO.builder()
            .id(bs.getId())
            .snackId(bs.getSnack().getSnackId())
            .snackName(bs.getSnack().getSnackName())
            .quantity(bs.getQuantity())
            .priceAtPurchase(bs.getPriceAtPurchase())
            .subtotal(bs.getSubtotal())
            .build();
    }

    private boolean isExpiredSnack(Snack snack) {
        return snack.getExpiryDate() != null && snack.getExpiryDate().isBefore(LocalDate.now());
    }

    private SnackWarehouseDTO convertToWarehouseDTO(Snack snack) {
        double stock = snack.getWarehouseStock() == null ? 0.0 : snack.getWarehouseStock();
        double reorder = snack.getWarehouseReorderLevel() == null ? 0.0 : snack.getWarehouseReorderLevel();
        return SnackWarehouseDTO.builder()
            .snackId(snack.getSnackId())
            .snackName(snack.getSnackName())
            .category(snack.getCategory().name())
            .warehouseTrackable(snack.getWarehouseTrackable())
            .warehouseStock(stock)
            .warehouseReorderLevel(reorder)
            .lowStock(stock <= reorder)
            .build();
    }

    private boolean isWarehouseTrackable(Snack snack) {
        return Boolean.TRUE.equals(snack.getWarehouseTrackable());
    }
}
