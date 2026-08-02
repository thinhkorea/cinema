package com.example.cinema.repository;

import com.example.cinema.domain.SnackOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SnackOrderRepository extends JpaRepository<SnackOrder, Long> {

    @EntityGraph(attributePaths = {"customer.user"})
    Optional<SnackOrder> findByOrderCode(String orderCode);

    @EntityGraph(attributePaths = {"customer.user"})
    List<SnackOrder> findByCustomer_CustomerIdOrderByCreatedAtDesc(Long customerId);

    List<SnackOrder> findAllByStatusAndOrderTypeAndCreatedAtBefore(
            SnackOrder.Status status,
            SnackOrder.OrderType orderType,
            LocalDateTime timestamp);

    @EntityGraph(attributePaths = {"booking", "customer.user"})
    Optional<SnackOrder> findFirstByBookingTxnRefOrderByCreatedAtDesc(String bookingTxnRef);

    @Query("""
           SELECT COALESCE(SUM(o.totalAmount), 0)
           FROM SnackOrder o
           WHERE o.customer.user.userId = :userId
             AND o.status = com.example.cinema.domain.SnackOrder$Status.PAID
             AND o.orderType = com.example.cinema.domain.SnackOrder$OrderType.STANDALONE
           """)
    Double sumPaidStandaloneAmountByCustomerUserId(@Param("userId") Long userId);

    @Query("""
           SELECT COALESCE(SUM(o.totalAmount), 0)
           FROM SnackOrder o
           WHERE o.customer.user.userId = :userId
             AND o.status = com.example.cinema.domain.SnackOrder$Status.PAID
             AND o.orderType = com.example.cinema.domain.SnackOrder$OrderType.STANDALONE
             AND (
                 (o.paidAt IS NOT NULL AND o.paidAt >= :from)
                 OR (o.paidAt IS NULL AND o.createdAt >= :from)
             )
           """)
    Double sumPaidStandaloneAmountByCustomerUserIdSince(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from);

    @Query("""
           SELECT c.customerId AS customerId,
                  u.fullName AS customerName,
                  u.email AS email,
                  u.phone AS phone,
                  COUNT(o.snackOrderId) AS snackOrderCount,
                  SUM(o.totalAmount) AS totalSpent
           FROM SnackOrder o
           JOIN o.customer c
           JOIN c.user u
           WHERE o.status = com.example.cinema.domain.SnackOrder$Status.PAID
             AND (
                 (o.paidAt IS NOT NULL AND o.paidAt >= :from AND o.paidAt < :to)
                 OR (o.paidAt IS NULL AND o.createdAt >= :from AND o.createdAt < :to)
             )
           GROUP BY c.customerId, u.fullName, u.email, u.phone
           ORDER BY totalSpent DESC
           """)
    List<Object[]> getCustomerSnackSpendingBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
