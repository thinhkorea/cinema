package com.example.cinema.repository;

import com.example.cinema.domain.VoucherClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VoucherClaimRepository extends JpaRepository<VoucherClaim, Long> {
    boolean existsByVoucher_VoucherIdAndUser_UserId(Long voucherId, Long userId);

    Optional<VoucherClaim> findByVoucher_VoucherIdAndUser_UserId(Long voucherId, Long userId);
}
