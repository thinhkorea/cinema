package com.example.cinema.repository;

import com.example.cinema.domain.IngredientBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface IngredientBatchRepository extends JpaRepository<IngredientBatch, Long> {

    List<IngredientBatch> findByIngredient_IngredientIdOrderByReceivedAtDesc(Long ingredientId);

    List<IngredientBatch> findByExpiryDateLessThanEqualAndQuantityRemainingGreaterThanOrderByExpiryDateAsc(
            LocalDate date,
            Double quantity);
}
