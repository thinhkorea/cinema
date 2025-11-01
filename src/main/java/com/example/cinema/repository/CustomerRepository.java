package com.example.cinema.repository;

import com.example.cinema.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // Tìm khách hàng thông qua username của user liên kết
    Optional<Customer> findByUser_Username(String username);

    // Tìm khách hàng thông qua userId
    Optional<Customer> findByUser_UserId(Long userId);

    // Kiểm tra khách hàng có tồn tại bằng email không
    boolean existsByEmail(String email);
}
