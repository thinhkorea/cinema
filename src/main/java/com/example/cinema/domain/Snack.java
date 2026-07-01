package com.example.cinema.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "snacks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Snack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long snackId;

    @Column(nullable = false, length = 100)
    private String snackName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Double price;

    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SnackCategory category = SnackCategory.OTHER;

    @Column(nullable = false)
    private Boolean available = true;

    @Column(nullable = false)
    private Boolean warehouseTrackable = false;

    @Column(nullable = false)
    private Double warehouseStock = 0.0;

    @Column(nullable = false)
    private Double warehouseReorderLevel = 10.0;

    private LocalDate expiryDate;

    @Column(columnDefinition = "TEXT")
    private String recipeInstructions;

    @Lob
    @Column(name = "search_embedding", columnDefinition = "TEXT")
    private String searchEmbedding;

    public enum SnackCategory {
        COMBO,     
        DRINK,       
        SNACK,
        OTHER    
    }
}
