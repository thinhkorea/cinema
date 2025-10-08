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

    private String posterUrl; // ảnh poster (sẽ dùng sau nếu muốn hiển thị)
    private String trailerUrl; // link trailer YouTube (để mở rộng sau)
}
