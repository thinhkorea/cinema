package com.example.cinema.controller;

import com.example.cinema.domain.Customer;
import com.example.cinema.domain.User;
import com.example.cinema.repository.CustomerRepository;
import com.example.cinema.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@CrossOrigin(origins = "*")
public class CustomerController {

    private final CustomerRepository customerRepo;
    private final UserRepository userRepo;

    public CustomerController(CustomerRepository customerRepo, UserRepository userRepo) {
        this.customerRepo = customerRepo;
        this.userRepo = userRepo;
    }

    // Lấy danh sách tất cả khách hàng
    @GetMapping
    public ResponseEntity<List<Customer>> getAllCustomers() {
        return ResponseEntity.ok(customerRepo.findAll());
    }

    // Xem chi tiết 1 khách hàng
    @GetMapping("/{id}")
    public ResponseEntity<?> getCustomerById(@PathVariable Long id) {
        return customerRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Tạo khách hàng mới (thường dùng khi đăng ký user)
    @PostMapping
    public ResponseEntity<?> createCustomer(@RequestBody Customer customer) {
        if (customer.getUser() != null && customer.getUser().getUserId() != null) {
            User user = userRepo.findById(customer.getUser().getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            customer.setUser(user);
        }
        return ResponseEntity.ok(customerRepo.save(customer));
    }

    // Cập nhật thông tin khách hàng
    @PutMapping("/{id}")
    public ResponseEntity<?> updateCustomer(@PathVariable Long id, @RequestBody Customer updated) {
        return customerRepo.findById(id)
                .map(c -> {
                    c.setGender(updated.getGender());
                    c.setAddress(updated.getAddress());
                    return ResponseEntity.ok(customerRepo.save(c));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Xóa khách hàng
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCustomer(@PathVariable Long id) {
        customerRepo.deleteById(id);
        return ResponseEntity.ok().body("Customer deleted successfully");
    }
}
