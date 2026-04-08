package com.example.cinema.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @Column(nullable = false)
    private Integer stock = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SnackCategory category = SnackCategory.OTHER;

    @Column(nullable = false)
    private Boolean available = true;

    public enum SnackCategory {
        COMBO,     
        DRINK,       
        SNACK,
        OTHER    
    }
}
