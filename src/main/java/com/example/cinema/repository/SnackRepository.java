package com.example.cinema.repository;

import com.example.cinema.domain.Snack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SnackRepository extends JpaRepository<Snack, Long> {
    
    /**
     * Tìm tất cả snacks theo category
     */
    List<Snack> findByCategory(Snack.SnackCategory category);
    
    /**
     * Tìm tất cả snacks đang available
     */
    List<Snack> findByAvailableTrue();
    
    /**
     * Tìm snacks available theo category
     */
    List<Snack> findByCategoryAndAvailableTrue(Snack.SnackCategory category);
    
    /**
     * Tìm snacks còn hàng (stock > 0)
     */
    List<Snack> findByAvailableTrueAndStockGreaterThan(Integer minStock);
}
