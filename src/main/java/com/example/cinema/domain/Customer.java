package com.example.cinema.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "customers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long customerId;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @JsonIgnoreProperties({ "customer" })
    private User user;

    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private Gender gender;
    private Integer loyaltyPoints = 0;

    public enum Gender {
        MALE,
        FEMALE
    }
}
