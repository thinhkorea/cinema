package com.example.cinema.repository;

import com.example.cinema.domain.PointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    List<PointTransaction> findByCustomer_CustomerIdOrderByCreatedAtDesc(Long customerId);

    boolean existsByCustomer_CustomerId(Long customerId);

    @Query("SELECT COALESCE(SUM(pt.points), 0) FROM PointTransaction pt " +
           "WHERE pt.customer.customerId = :customerId " +
           "AND (pt.expiredAt IS NULL OR pt.expiredAt > :now)")
    Integer sumAvailablePoints(@Param("customerId") Long customerId, @Param("now") LocalDateTime now);

    @Query("SELECT pt FROM PointTransaction pt " +
           "WHERE pt.customer.customerId = :customerId " +
           "AND pt.type = 'REFUND_CANCEL' " +
           "AND pt.expiredAt IS NOT NULL " +
           "AND pt.expiredAt <= :now " +
           "ORDER BY pt.expiredAt ASC")
    List<PointTransaction> findExpiredRefundTransactions(@Param("customerId") Long customerId,
                                                         @Param("now") LocalDateTime now);
}
