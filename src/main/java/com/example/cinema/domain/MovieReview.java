package com.example.cinema.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "movie_reviews", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "movie_id", "user_id" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class MovieReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reviewId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Integer rating;

    @Column(length = 1000)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ModerationStatus moderationStatus = ModerationStatus.APPROVED;

    @Column
    private Boolean flagged = false;

    @Column(length = 50)
    private String violationType;

    @Column(length = 30)
    private String violationSeverity;

    @Column(length = 1000)
    private String violationReason;

    @Column(length = 50)
    private String moderationProvider;

    private LocalDateTime moderatedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum ModerationStatus {
        APPROVED,
        FLAGGED,
        REJECTED,
        PENDING_REVIEW
    }
}
