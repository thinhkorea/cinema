package com.example.cinema.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_violation_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class UserViolationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long violationLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id")
    private MovieReview review;

    @Column(nullable = false, length = 50)
    private String sourceType;

    @Column(nullable = false, length = 50)
    private String violationType;

    @Column(nullable = false, length = 30)
    private String severity;

    @Column(length = 1000)
    private String reason;

    @Column(length = 1000)
    private String contentSnapshot;

    @Column(length = 50)
    private String moderationProvider;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
