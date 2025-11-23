package com.example.cinema.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "movies")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long movieId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Integer duration;

    private String genre;

    @Column(length = 2000)
    private String description;

    private String posterUrl;
    private String trailerUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private MovieStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_rating")
    private AgeRating ageRating;

    @Column(length = 2000)
    private String actors;

    public enum MovieStatus {
        NOW_SHOWING,
        COMING_SOON,
        SPECIAL_RELEASE,
        ENDED
    }

    public enum AgeRating {
        P,      // Phổ thông
        C13,    // Cấm dưới 13 tuổi
        C16,    // Cấm dưới 16 tuổi
        C18     // Cấm dưới 18 tuổi
    }
}
