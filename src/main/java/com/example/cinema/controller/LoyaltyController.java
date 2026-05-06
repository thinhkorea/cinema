package com.example.cinema.controller;

import com.example.cinema.domain.Customer;
import com.example.cinema.dto.PointTransactionDTO;
import com.example.cinema.repository.CustomerRepository;
import com.example.cinema.service.PointService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/loyalty")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class LoyaltyController {

    private final CustomerRepository customerRepository;
    private final PointService pointService;

    public LoyaltyController(CustomerRepository customerRepository, PointService pointService) {
        this.customerRepository = customerRepository;
        this.pointService = pointService;
    }

    // Lấy điểm khả dụng (đã trừ hết hạn) của khách hàng
    @GetMapping("/points/{customerId}")
    public ResponseEntity<?> getCustomerPoints(@PathVariable Long customerId) {
        try {
            int available = pointService.getAvailablePoints(customerId);
            return ResponseEntity.ok(Map.of(
                "customerId", customerId,
                "availablePoints", available,
                "pointsValue", available * 1000.0,
                "message", available + " điểm khả dụng = " + (available * 1000.0) + "đ giảm giá"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Lịch sử giao dịch điểm (bao gồm hạn sử dụng)
    @GetMapping("/points/{customerId}/history")
    public ResponseEntity<?> getPointHistory(@PathVariable Long customerId) {
        try {
            List<PointTransactionDTO> history = pointService.getHistory(customerId);
            int available = pointService.getAvailablePoints(customerId);
            return ResponseEntity.ok(Map.of(
                "customerId", customerId,
                "availablePoints", available,
                "transactions", history
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Lấy điểm của chính khách hàng đang đăng nhập
    @GetMapping("/my-points")
    public ResponseEntity<?> getMyPoints(Authentication authentication) {
        try {
            String username = authentication.getName();
            Customer customer = customerRepository.findByUser_Email(username)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ khách hàng"));
            int available = pointService.getAvailablePoints(customer.getCustomerId());
            List<PointTransactionDTO> history = pointService.getHistory(customer.getCustomerId());
            return ResponseEntity.ok(Map.of(
                "customerId", customer.getCustomerId(),
                "availablePoints", available,
                "pointsValue", available * 1000.0,
                "transactions", history
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Cộng điểm thủ công cho khách hàng (Admin/Staff)
    @PostMapping("/points/{customerId}/add")
    public ResponseEntity<?> addPoints(@PathVariable Long customerId, @RequestBody Map<String, Integer> request) {
        try {
            Integer pointsToAdd = request.get("points");
            Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Khách hàng không tồn tại"));

            Integer currentPoints = customer.getLoyaltyPoints() != null ? customer.getLoyaltyPoints() : 0;
            customer.setLoyaltyPoints(currentPoints + pointsToAdd);
            customerRepository.save(customer);

            return ResponseEntity.ok(Map.of(
                "customerId", customerId,
                "addedPoints", pointsToAdd,
                "totalPoints", customer.getLoyaltyPoints(),
                "message", "Cộng " + pointsToAdd + " điểm thành công"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
