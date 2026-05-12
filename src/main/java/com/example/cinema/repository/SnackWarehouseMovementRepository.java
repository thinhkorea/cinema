package com.example.cinema.repository;

import com.example.cinema.domain.SnackWarehouseMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SnackWarehouseMovementRepository extends JpaRepository<SnackWarehouseMovement, Long> {

    List<SnackWarehouseMovement> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);

    List<SnackWarehouseMovement> findTop50BySnack_SnackIdOrderByCreatedAtDesc(Long snackId);
}
