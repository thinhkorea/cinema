package com.example.cinema.repository;

import com.example.cinema.domain.IngredientStockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface IngredientStockMovementRepository extends JpaRepository<IngredientStockMovement, Long> {

    List<IngredientStockMovement> findByIngredient_IngredientIdOrderByCreatedAtDesc(Long ingredientId);

    List<IngredientStockMovement> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);
}
