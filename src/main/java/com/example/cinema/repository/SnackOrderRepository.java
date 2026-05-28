package com.example.cinema.repository;

import com.example.cinema.domain.SnackOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SnackOrderRepository extends JpaRepository<SnackOrder, Long> {

    @EntityGraph(attributePaths = {"customer.user"})
    Optional<SnackOrder> findByOrderCode(String orderCode);

    @EntityGraph(attributePaths = {"customer.user"})
    List<SnackOrder> findByCustomer_CustomerIdOrderByCreatedAtDesc(Long customerId);

    @EntityGraph(attributePaths = {"booking", "customer.user"})
    Optional<SnackOrder> findFirstByBookingTxnRefOrderByCreatedAtDesc(String bookingTxnRef);
}
