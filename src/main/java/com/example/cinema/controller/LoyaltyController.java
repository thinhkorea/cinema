package com.example.cinema.controller;

import com.example.cinema.domain.Customer;
import com.example.cinema.repository.CustomerRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/loyalty")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class LoyaltyController {
    
    private final CustomerRepository customerRepository;
    
    public LoyaltyController(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }
    
    // Lấy điểm của khách hàng
    @GetMapping("/points/{customerId}")
    public Map<String, Object> getCustomerPoints(@PathVariable Long customerId) {
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new RuntimeException("Khách hàng không tồn tại"));
        
        Integer points = customer.getLoyaltyPoints() != null ? customer.getLoyaltyPoints() : 0;
        
        return Map.of(
            "customerId", customerId,
            "loyaltyPoints", points,
            "pointsValue", points * 1000.0,
            "message", points + " điểm (tích từ " + (points * 20000.0) + "đ) = " + (points * 1000.0) + "đ giảm giá"
        );
    }
    
    // Cộng điểm cho khách hàng (Admin/Staff)
    @PostMapping("/points/{customerId}/add")
    public Map<String, Object> addPoints(@PathVariable Long customerId, @RequestBody Map<String, Integer> request) {
        Integer pointsToAdd = request.get("points");
        
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new RuntimeException("Khách hàng không tồn tại"));
        
        Integer currentPoints = customer.getLoyaltyPoints() != null ? customer.getLoyaltyPoints() : 0;
        customer.setLoyaltyPoints(currentPoints + pointsToAdd);
        customerRepository.save(customer);
        
        return Map.of(
            "customerId", customerId,
            "addedPoints", pointsToAdd,
            "totalPoints", customer.getLoyaltyPoints(),
            "message", "Cộng " + pointsToAdd + " điểm thành công"
        );
    }
    
    // Sử dụng điểm để giảm giá
    @PostMapping("/points/{customerId}/redeem")
    public Map<String, Object> redeemPoints(@PathVariable Long customerId, @RequestBody Map<String, Integer> request) {
        Integer pointsToUse = request.get("points");
        
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new RuntimeException("Khách hàng không tồn tại"));
        
        Integer currentPoints = customer.getLoyaltyPoints() != null ? customer.getLoyaltyPoints() : 0;
        
        if (currentPoints < pointsToUse) {
            throw new IllegalArgumentException("Điểm không đủ! Bạn có " + currentPoints + " điểm");
        }
        
        customer.setLoyaltyPoints(currentPoints - pointsToUse);
        customerRepository.save(customer);
        
        Double discount = pointsToUse * 1000.0;
        
        return Map.of(
            "customerId", customerId,
            "usedPoints", pointsToUse,
            "discount", discount,
            "remainingPoints", customer.getLoyaltyPoints(),
            "message", "Sử dụng " + pointsToUse + " điểm, được giảm " + discount + "đ"
        );
    }
}
