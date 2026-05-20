package com.example.cinema.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "staff_shifts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long shiftId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Column(nullable = false)
    private LocalDateTime openedAt;

    @Column
    private LocalDateTime closedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column
    private Double expectedCash = 0.0;

    @Column
    private Double actualCash = 0.0;

    @Column
    private Double cashDifference = 0.0;

    @Column(length = 500)
    private String note;

    public enum Status {
        OPEN,
        CLOSED
    }
}
