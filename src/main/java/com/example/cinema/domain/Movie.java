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
    private Integer duration; // đơn vị phút

    private String genre;

    @Column(length = 2000)
    private String description;

    private String posterUrl;
    private String trailerUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private MovieStatus status; // Sử dụng inner enum bên dưới

    // Định nghĩa enum ngay trong class Movie
    public enum MovieStatus {
        NOW_SHOWING, // Đang chiếu
        COMING_SOON, // Sắp chiếu
        SPECIAL_RELEASE, // Suất chiếu đặc biệt
        ENDED // Đã kết thúc
    }
}
