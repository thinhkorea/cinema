package com.example.cinema.controller;

import com.example.cinema.domain.Snack;
import com.example.cinema.dto.AddSnacksRequest;
import com.example.cinema.dto.BookingSnackDTO;
import com.example.cinema.dto.SnackDTO;
import com.example.cinema.service.SnackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    public ResponseEntity<Snack> getSnackById(@PathVariable Long snackId) {
        Snack snack = snackService.getSnackById(snackId);
        return ResponseEntity.ok(snack);
    }

    /**
     * [ADMIN] Tạo snack mới
     * POST /api/snacks
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Snack> createSnack(@RequestBody Snack snack) {
        Snack createdSnack = snackService.createSnack(snack);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdSnack);
    }

    /**
     * [ADMIN] Cập nhật snack
     * PUT /api/snacks/{snackId}
     */
    @PutMapping("/{snackId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Snack> updateSnack(
            @PathVariable Long snackId,
            @RequestBody Snack snackDetails) {
        Snack updatedSnack = snackService.updateSnack(snackId, snackDetails);
        return ResponseEntity.ok(updatedSnack);
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
            @RequestBody AddSnacksRequest request) {
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
