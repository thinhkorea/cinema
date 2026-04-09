package com.example.cinema.repository;

import com.example.cinema.domain.RegistrationOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegistrationOtpRepository extends JpaRepository<RegistrationOtp, Long> {
    Optional<RegistrationOtp> findTopByEmailOrderByCreatedAtDesc(String email);

    void deleteByEmail(String email);
}
