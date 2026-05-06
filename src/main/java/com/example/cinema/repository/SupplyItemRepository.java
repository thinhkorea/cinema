package com.example.cinema.repository;

import com.example.cinema.domain.SupplyItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplyItemRepository extends JpaRepository<SupplyItem, Long> {
}
