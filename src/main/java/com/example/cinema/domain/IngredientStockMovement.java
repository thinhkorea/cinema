package com.example.cinema.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ingredient_stock_movements")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngredientStockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long movementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(nullable = false)
    private Double quantityBefore;

    @Column(nullable = false)
    private Double quantityChange;

    @Column(nullable = false)
    private Double quantityAfter;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(length = 255)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(nullable = false, length = 120)
    private String performedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
