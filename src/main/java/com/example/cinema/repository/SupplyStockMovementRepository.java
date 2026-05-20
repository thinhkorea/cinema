package com.example.cinema.repository;

import com.example.cinema.domain.SupplyStockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SupplyStockMovementRepository extends JpaRepository<SupplyStockMovement, Long> {

    List<SupplyStockMovement> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);

    List<SupplyStockMovement> findTop50BySupply_SupplyIdOrderByCreatedAtDesc(Long supplyId);

    void deleteBySupply_SupplyId(Long supplyId);
}
