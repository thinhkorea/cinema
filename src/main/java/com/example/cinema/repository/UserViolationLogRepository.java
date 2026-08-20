package com.example.cinema.repository;

import com.example.cinema.domain.UserViolationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserViolationLogRepository extends JpaRepository<UserViolationLog, Long> {

    List<UserViolationLog> findTop100ByOrderByCreatedAtDesc();

    List<UserViolationLog> findByUser_UserIdOrderByCreatedAtDesc(Long userId);

    List<UserViolationLog> findBySourceTypeAndCreatedAtAfterOrderByCreatedAtDesc(
            String sourceType,
            LocalDateTime createdAt);

    long countByUser_UserIdAndSourceTypeAndViolationTypeAndCreatedAtAfter(
            Long userId,
            String sourceType,
            String violationType,
            LocalDateTime createdAt);

    long countByUser_UserIdAndSourceTypeAndCreatedAtAfter(
            Long userId,
            String sourceType,
            LocalDateTime createdAt);

    void deleteByReview_ReviewId(Long reviewId);
}
