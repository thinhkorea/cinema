package com.example.cinema.service;

import com.example.cinema.domain.SnackOrder;
import com.example.cinema.repository.SnackOrderRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SnackOrderCleanupService {

    private static final int PENDING_SNACK_ORDER_TIMEOUT_MINUTES = 30;

    private final SnackOrderRepository snackOrderRepository;

    public SnackOrderCleanupService(SnackOrderRepository snackOrderRepository) {
        this.snackOrderRepository = snackOrderRepository;
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanupPendingStandaloneSnackOrders() {
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(PENDING_SNACK_ORDER_TIMEOUT_MINUTES);

        List<SnackOrder> expiredOrders = snackOrderRepository.findAllByStatusAndOrderTypeAndCreatedAtBefore(
                SnackOrder.Status.PENDING,
                SnackOrder.OrderType.STANDALONE,
                timeoutThreshold);

        if (!expiredOrders.isEmpty()) {
            snackOrderRepository.deleteAll(expiredOrders);
        }
    }
}
