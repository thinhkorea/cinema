package com.example.cinema.repository;

import com.example.cinema.domain.SnackRecipeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SnackRecipeItemRepository extends JpaRepository<SnackRecipeItem, Long> {

    List<SnackRecipeItem> findBySnack_SnackIdOrderByIngredient_IngredientNameAsc(Long snackId);

    void deleteBySnack_SnackId(Long snackId);
}
