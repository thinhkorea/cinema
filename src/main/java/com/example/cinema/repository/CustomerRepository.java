package com.example.cinema.repository;

import com.example.cinema.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // Tìm khách hàng thông qua email của user liên kết
    Optional<Customer> findByUser_Email(String email);

    // Tìm khách hàng thông qua userId
    Optional<Customer> findByUserUserId(Long userId);
}
