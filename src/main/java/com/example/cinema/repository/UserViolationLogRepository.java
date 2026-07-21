package com.example.cinema.repository;

import com.example.cinema.domain.UserViolationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserViolationLogRepository extends JpaRepository<UserViolationLog, Long> {

    List<UserViolationLog> findTop100ByOrderByCreatedAtDesc();

    List<UserViolationLog> findByUser_UserIdOrderByCreatedAtDesc(Long userId);

    void deleteByReview_ReviewId(Long reviewId);
}
