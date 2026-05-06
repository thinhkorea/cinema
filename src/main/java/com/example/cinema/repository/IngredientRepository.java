package com.example.cinema.repository;

import com.example.cinema.domain.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {
	boolean existsByIngredientNameIgnoreCase(String ingredientName);

	boolean existsByIngredientNameIgnoreCaseAndIngredientIdNot(String ingredientName, Long ingredientId);
}
