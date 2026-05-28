package com.example.cinema.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long roomId;

    @Column(nullable = false, unique = true)
    private String roomName;

    @Column(nullable = false)
    private Integer capacity;

    private String roomType; // 2D, 3D, IMAX...

    @Column(columnDefinition = "TEXT")
    private String layoutConfig;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean active = true;
}
