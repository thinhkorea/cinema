package com.example.cinema.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.sql.Date;

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

    private String genre;
    private Integer duration; // phút
    private String description;
    private Date releaseDate;
    private Date endDate;

    private String posterUrl; // đường dẫn ảnh poster
    private String trailerUrl; // đường dẫn trailer video (YouTube, MP4...)
}
