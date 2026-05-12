package com.example.cinema.controller;

import com.example.cinema.domain.Snack;
import com.example.cinema.dto.AddSnacksRequestDTO;
import com.example.cinema.dto.BookingSnackDTO;
import com.example.cinema.dto.SnackDTO;
import com.example.cinema.dto.SnackWarehouseDTO;
import com.example.cinema.dto.UpdateSnackWarehouseStockRequestDTO;
import com.example.cinema.service.SnackService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/snacks")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RequiredArgsConstructor
public class SnackController {

    private final SnackService snackService;

    /**
     * [PUBLIC] Lấy tất cả snacks đang available
     * GET /api/snacks
     */
    @GetMapping
    public ResponseEntity<List<SnackDTO>> getAllAvailableSnacks() {
        List<SnackDTO> snacks = snackService.getAllAvailableSnacks();
        return ResponseEntity.ok(snacks);
    }

    /**
     * [PUBLIC] Lấy snacks theo category
     * GET /api/snacks/category/{category}
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<List<SnackDTO>> getSnacksByCategory(
            @PathVariable Snack.SnackCategory category) {
        List<SnackDTO> snacks = snackService.getSnacksByCategory(category);
        return ResponseEntity.ok(snacks);
    }

    /**
     * [PUBLIC] Lấy snack theo ID
     * GET /api/snacks/{snackId}
     */
    @GetMapping("/{snackId}")
    public ResponseEntity<SnackDTO> getSnackById(@PathVariable Long snackId) {
        SnackDTO dto = snackService.getSnackDTOById(snackId);
        return ResponseEntity.ok(dto);
    }

    /**
     * [ADMIN] Tạo snack mới
     * POST /api/snacks
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SnackDTO> createSnack(@RequestBody Snack snack) {
        SnackDTO created = snackService.createSnackAndReturnDTO(snack);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/admin/upload-image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> uploadSnackImage(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Vui lòng chọn ảnh."));
            }

            String contentType = file.getContentType() == null ? "" : file.getContentType();
            if (!contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body(Map.of("error", "File upload phải là ảnh."));
            }

            String originalName = file.getOriginalFilename() == null ? "snack" : file.getOriginalFilename();
            String extension = "";
            int dotIndex = originalName.lastIndexOf('.');
            if (dotIndex >= 0) {
                extension = originalName.substring(dotIndex).replaceAll("[^a-zA-Z0-9.]", "");
            }
            String fileName = UUID.randomUUID() + extension;
            Path uploadDir = Paths.get("uploads", "snacks");
            Files.createDirectories(uploadDir);
            Files.copy(file.getInputStream(), uploadDir.resolve(fileName));

            return ResponseEntity.ok(Map.of("imageUrl", "http://localhost:8080/api/snacks/images/" + fileName));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Không thể upload ảnh: " + e.getMessage()));
        }
    }

    @GetMapping("/images/{fileName:.+}")
    public ResponseEntity<Resource> getSnackImage(@PathVariable String fileName) throws MalformedURLException {
        Path imagePath = Paths.get("uploads", "snacks").resolve(fileName).normalize();
        Resource resource = new UrlResource(imagePath.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(resource);
    }

    /**
     * [ADMIN] Lấy tất cả snack (bao gồm hết hàng/không available)
     * GET /api/snacks/admin/all
     */
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SnackDTO>> getAllSnacksForAdmin() {
        return ResponseEntity.ok(snackService.getAllSnacksForAdmin());
    }

    /**
     * [ADMIN] Danh sách snack sắp hết hạn
     * GET /api/snacks/admin/expiring-soon?days=7
     */
    @GetMapping("/admin/expiring-soon")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SnackDTO>> getExpiringSoonSnacks(
            @RequestParam(defaultValue = "7") Integer days) {
        return ResponseEntity.ok(snackService.getExpiringSoonSnacks(days));
    }

    @GetMapping("/admin/warehouse-stocks")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SnackWarehouseDTO>> getWarehouseStocks() {
        return ResponseEntity.ok(snackService.getSnackWarehouseStocks());
    }

    @GetMapping("/admin/warehouse-stocks/low")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SnackWarehouseDTO>> getLowWarehouseStocks(
            @RequestParam(required = false) Double threshold) {
        return ResponseEntity.ok(snackService.getLowWarehouseStocks(threshold));
    }

    @GetMapping("/admin/{snackId}/warehouse-movements")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getWarehouseMovements(@PathVariable Long snackId) {
        return ResponseEntity.ok(snackService.getSnackWarehouseMovements(snackId));
    }

    @PatchMapping("/admin/{snackId}/warehouse-stock")
    @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<?> updateWarehouseStock(
            @PathVariable Long snackId,
            @RequestBody UpdateSnackWarehouseStockRequestDTO request,
            Authentication authentication) {
        try {
            String actor = authentication != null ? authentication.getName() : "admin";
            SnackWarehouseDTO result = snackService.updateWarehouseStock(snackId, request, actor);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * [ADMIN] Cập nhật snack
     * PUT /api/snacks/{snackId}
     */
    @PutMapping("/{snackId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SnackDTO> updateSnack(
            @PathVariable Long snackId,
            @RequestBody Snack snackDetails) {
        SnackDTO updated = snackService.updateSnackAndReturnDTO(snackId, snackDetails);
        return ResponseEntity.ok(updated);
    }

    /**
     * [ADMIN] Xóa snack
     * DELETE /api/snacks/{snackId}
     */
    @DeleteMapping("/{snackId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteSnack(@PathVariable Long snackId) {
        snackService.deleteSnack(snackId);
        return ResponseEntity.ok(Map.of("message", "Snack deleted successfully"));
    }

    /**
     * [CUSTOMER/STAFF] Thêm snacks vào bookings của một transaction
     * POST /api/snacks/add-to-booking
     * Body: { "txnRef": "...", "snacks": [{ "snackId": 1, "quantity": 2 }, ...] }
     */
    @PostMapping("/add-to-booking")
        public ResponseEntity<Map<String, Object>> addSnacksToBooking(
            @RequestBody AddSnacksRequestDTO request) {
        try {
            Map<String, Object> result = snackService.addSnacksToBookings(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * [PUBLIC] Lấy snacks của một booking
     * GET /api/snacks/booking/{bookingId}
     */
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<List<BookingSnackDTO>> getBookingSnacks(@PathVariable Long bookingId) {
        List<BookingSnackDTO> snacks = snackService.getBookingSnacks(bookingId);
        return ResponseEntity.ok(snacks);
    }

    /**
     * [PUBLIC] Lấy snacks của một transaction (txnRef)
     * GET /api/snacks/txn/{txnRef}
     */
    @GetMapping("/txn/{txnRef}")
    public ResponseEntity<List<BookingSnackDTO>> getSnacksByTxnRef(@PathVariable String txnRef) {
        List<BookingSnackDTO> snacks = snackService.getSnacksByTxnRef(txnRef);
        return ResponseEntity.ok(snacks);
    }

    /**
     * [PUBLIC] Tính tổng tiền snacks của một transaction
     * GET /api/snacks/txn/{txnRef}/total
     */
    @GetMapping("/txn/{txnRef}/total")
    public ResponseEntity<Map<String, Double>> getSnacksTotalByTxnRef(@PathVariable String txnRef) {
        Double total = snackService.calculateSnacksTotalByTxnRef(txnRef);
        return ResponseEntity.ok(Map.of("totalSnacksCost", total));
    }
}
