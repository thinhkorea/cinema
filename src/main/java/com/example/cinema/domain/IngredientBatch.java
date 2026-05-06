package com.example.cinema.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ingredient_batches")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngredientBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long batchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(nullable = false)
    private Double quantityReceived;

    @Column(nullable = false)
    private Double quantityRemaining;

    private Double unitCost;

    @Column(length = 180)
    private String supplier;

    private LocalDate productionDate;

    private LocalDate expiryDate;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    @Column(columnDefinition = "TEXT")
    private String note;

    @PrePersist
    public void prePersist() {
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
    }
}
