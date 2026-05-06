package com.example.cinema.repository;

import com.example.cinema.domain.VoucherRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherRedemptionRepository extends JpaRepository<VoucherRedemption, Long> {
    int countByVoucher_VoucherIdAndUser_UserId(Long voucherId, Long userId);

    boolean existsByVoucher_VoucherIdAndTxnRef(Long voucherId, String txnRef);
}
