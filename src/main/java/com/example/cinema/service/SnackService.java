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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SnackService {

    private final SnackRepository snackRepository;
    private final BookingSnackRepository bookingSnackRepository;
    private final BookingRepository bookingRepository;
    private final SnackWarehouseMovementRepository snackWarehouseMovementRepository;

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
        return snackRepository.save(snack);
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
        if (request == null || request.getQuantity() == null || request.getQuantity() < 0) {
            throw new IllegalArgumentException("quantity phải >= 0");
        }

        Snack snack = getSnackById(snackId);
        if (!isWarehouseTrackable(snack)) {
            throw new IllegalArgumentException("Snack này không theo dõi tồn kho thành phẩm.");
        }

        String op = request.getOperation() == null ? "SET" : request.getOperation().trim().toUpperCase(Locale.ROOT);
        double current = snack.getWarehouseStock() == null ? 0.0 : snack.getWarehouseStock();
        double next;

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
        snackRepository.save(snack);

        SnackWarehouseMovement movement = new SnackWarehouseMovement();
        movement.setSnack(snack);
        movement.setQuantityBefore(current);
        movement.setQuantityChange(next - current);
        movement.setQuantityAfter(next);
        movement.setAction(op);
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
