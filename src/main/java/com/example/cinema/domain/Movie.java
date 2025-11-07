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

    public enum MovieStatus {
        NOW_SHOWING,
        COMING_SOON,
        SPECIAL_RELEASE,
        ENDED
    }
}
