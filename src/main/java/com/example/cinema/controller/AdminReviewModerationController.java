package com.example.cinema.controller;

import com.example.cinema.domain.Movie;
import com.example.cinema.domain.MovieReview;
import com.example.cinema.domain.User;
import com.example.cinema.domain.UserViolationLog;
import com.example.cinema.dto.MovieReviewResponseDTO;
import com.example.cinema.dto.ReviewModerationUserRiskDTO;
import com.example.cinema.dto.UserViolationLogResponseDTO;
import com.example.cinema.repository.MovieReviewRepository;
import com.example.cinema.repository.UserViolationLogRepository;
import com.example.cinema.service.ReviewModerationService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/review-moderation")
public class AdminReviewModerationController {

    private static final Charset WINDOWS_1252 = Charset.forName("Windows-1252");
    private static final String REPLACEMENT_CHARACTER = String.valueOf((char) 65533);
    private static final String MOVIE_REVIEW_SOURCE = "MOVIE_REVIEW";
    private static final String SPAM_VIOLATION_TYPE = "SPAM";
    private static final int SPAM_VIOLATION_LIMIT = 3;
    private static final int SPAM_VIOLATION_WINDOW_HOURS = 24;
    private static final int REVIEW_VIOLATION_LIMIT = 5;
    private static final int REVIEW_VIOLATION_WINDOW_DAYS = 7;

    private final MovieReviewRepository reviewRepo;
    private final UserViolationLogRepository violationLogRepo;
    private final ReviewModerationService reviewModerationService;

    public AdminReviewModerationController(
            MovieReviewRepository reviewRepo,
            UserViolationLogRepository violationLogRepo,
            ReviewModerationService reviewModerationService) {
        this.reviewRepo = reviewRepo;
        this.violationLogRepo = violationLogRepo;
        this.reviewModerationService = reviewModerationService;
    }

    @GetMapping("/flagged-reviews")
    @Transactional(readOnly = true)
    public List<MovieReviewResponseDTO> getFlaggedReviews() {
        return reviewRepo.findReviewsNeedingAdminAttention(List.of(
                        MovieReview.ModerationStatus.FLAGGED,
                        MovieReview.ModerationStatus.PENDING_REVIEW))
                .stream()
                .map(this::toReviewResponse)
                .toList();
    }

    @GetMapping("/violations")
    @Transactional(readOnly = true)
    public List<UserViolationLogResponseDTO> getViolationLogs() {
        return violationLogRepo.findTop100ByOrderByCreatedAtDesc()
                .stream()
                .map(this::toViolationResponse)
                .toList();
    }

    @GetMapping("/suspicious-users")
    @Transactional(readOnly = true)
    public List<ReviewModerationUserRiskDTO> getSuspiciousUsers() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime spamWindowStart = now.minusHours(SPAM_VIOLATION_WINDOW_HOURS);
        LocalDateTime reviewViolationWindowStart = now.minusDays(REVIEW_VIOLATION_WINDOW_DAYS);

        Map<Long, UserRiskAccumulator> users = new HashMap<>();
        for (UserViolationLog log : violationLogRepo.findBySourceTypeAndCreatedAtAfterOrderByCreatedAtDesc(
                MOVIE_REVIEW_SOURCE,
                reviewViolationWindowStart)) {
            User user = log.getUser();
            if (user == null || user.getUserId() == null) {
                continue;
            }

            UserRiskAccumulator accumulator = users.computeIfAbsent(user.getUserId(), ignored -> new UserRiskAccumulator(user));
            accumulator.reviewViolations7d++;

            if (SPAM_VIOLATION_TYPE.equalsIgnoreCase(log.getViolationType())
                    && log.getCreatedAt() != null
                    && !log.getCreatedAt().isBefore(spamWindowStart)) {
                accumulator.spamViolations24h++;
            }

            if (accumulator.lastViolationAt == null
                    || (log.getCreatedAt() != null && log.getCreatedAt().isAfter(accumulator.lastViolationAt))) {
                accumulator.lastViolationAt = log.getCreatedAt();
                accumulator.lastViolationType = log.getViolationType();
                accumulator.lastSeverity = log.getSeverity();
                accumulator.lastReason = normalizeStoredText(log.getReason());
                accumulator.lastContentSnapshot = normalizeStoredText(log.getContentSnapshot());
            }
        }

        return users.values()
                .stream()
                .filter(UserRiskAccumulator::isReviewBlocked)
                .sorted(Comparator.comparing(UserRiskAccumulator::lastViolationAtOrMin).reversed())
                .map(UserRiskAccumulator::toResponse)
                .toList();
    }

    @PostMapping("/test")
    public ResponseEntity<?> testModeration(@RequestBody Map<String, String> request) {
        String comment = request.getOrDefault("comment", "").trim();
        ReviewModerationService.ModerationResult result = reviewModerationService.moderateMovieReview(comment);
        return ResponseEntity.ok(Map.of(
                "comment", comment,
                "flagged", result.flagged(),
                "violationType", result.violationType(),
                "severity", result.severity(),
                "confidence", result.confidence(),
                "reason", result.reason(),
                "provider", result.provider(),
                "checkedAt", LocalDateTime.now()
        ));
    }

    @PostMapping("/reviews/{reviewId}/approve")
    @Transactional
    public ResponseEntity<?> approveReview(@PathVariable Long reviewId) {
        MovieReview review = reviewRepo.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đánh giá"));

        review.setModerationStatus(MovieReview.ModerationStatus.APPROVED);
        review.setFlagged(false);
        review.setViolationType(null);
        review.setViolationSeverity(null);
        review.setViolationReason(null);
        review.setModerationProvider("ADMIN_REVIEW");
        review.setModeratedAt(LocalDateTime.now());
        reviewRepo.save(review);

        return ResponseEntity.ok(Map.of("message", "Đã duyệt đánh giá", "review", toReviewResponse(review)));
    }

    @PostMapping("/reviews/{reviewId}/reject")
    @Transactional
    public ResponseEntity<?> rejectReview(@PathVariable Long reviewId) {
        MovieReview review = reviewRepo.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đánh giá"));

        review.setModerationStatus(MovieReview.ModerationStatus.REJECTED);
        review.setFlagged(true);
        review.setModerationProvider("ADMIN_REVIEW");
        review.setModeratedAt(LocalDateTime.now());
        reviewRepo.save(review);

        return ResponseEntity.ok(Map.of("message", "Đã từ chối đánh giá", "review", toReviewResponse(review)));
    }

    private MovieReviewResponseDTO toReviewResponse(MovieReview review) {
        User user = review.getUser();
        Movie movie = review.getMovie();
        return MovieReviewResponseDTO.builder()
                .reviewId(review.getReviewId())
                .movieId(movie != null ? movie.getMovieId() : null)
                .movieTitle(movie != null ? movie.getTitle() : null)
                .userId(user != null ? user.getUserId() : null)
                .rating(review.getRating())
                .comment(review.getComment())
                .username(user != null ? user.getEmail() : "")
                .fullName(user != null && user.getFullName() != null ? user.getFullName() : (user != null ? user.getEmail() : ""))
                .moderationStatus(review.getModerationStatus() != null ? review.getModerationStatus().name() : null)
                .flagged(review.getFlagged())
                .violationType(review.getViolationType())
                .violationSeverity(review.getViolationSeverity())
                .violationReason(normalizeStoredText(review.getViolationReason()))
                .createdAt(review.getCreatedAt())
                .build();
    }

    private UserViolationLogResponseDTO toViolationResponse(UserViolationLog log) {
        User user = log.getUser();
        MovieReview review = log.getReview();
        Movie movie = review != null ? review.getMovie() : null;
        return UserViolationLogResponseDTO.builder()
                .violationLogId(log.getViolationLogId())
                .userId(user != null ? user.getUserId() : null)
                .username(user != null ? user.getEmail() : "")
                .fullName(user != null && user.getFullName() != null ? user.getFullName() : (user != null ? user.getEmail() : ""))
                .reviewId(review != null ? review.getReviewId() : null)
                .movieId(movie != null ? movie.getMovieId() : null)
                .movieTitle(movie != null ? movie.getTitle() : null)
                .sourceType(log.getSourceType())
                .violationType(log.getViolationType())
                .severity(log.getSeverity())
                .reason(normalizeStoredText(log.getReason()))
                .contentSnapshot(normalizeStoredText(log.getContentSnapshot()))
                .moderationProvider(log.getModerationProvider())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private String normalizeStoredText(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String normalized = value;
        for (int i = 0; i < 2; i++) {
            String decoded = repairStoredTextOnce(normalized);
            if (decoded.equals(normalized)) {
                return normalized;
            }
            normalized = decoded;
        }
        return normalized;
    }

    private String repairStoredTextOnce(String value) {
        try {
            String decoded = new String(value.getBytes(WINDOWS_1252), StandardCharsets.UTF_8);
            return decoded.contains(REPLACEMENT_CHARACTER) ? value : decoded;
        } catch (RuntimeException ignored) {
            return value;
        }
    }

    private static class UserRiskAccumulator {
        private final User user;
        private long spamViolations24h;
        private long reviewViolations7d;
        private LocalDateTime lastViolationAt;
        private String lastViolationType;
        private String lastSeverity;
        private String lastReason;
        private String lastContentSnapshot;

        private UserRiskAccumulator(User user) {
            this.user = user;
        }

        private boolean isReviewBlocked() {
            return spamViolations24h >= SPAM_VIOLATION_LIMIT || reviewViolations7d >= REVIEW_VIOLATION_LIMIT;
        }

        private LocalDateTime lastViolationAtOrMin() {
            return lastViolationAt != null ? lastViolationAt : LocalDateTime.MIN;
        }

        private ReviewModerationUserRiskDTO toResponse() {
            String riskLevel = spamViolations24h >= SPAM_VIOLATION_LIMIT ? "HIGH" : "MEDIUM";
            String recommendedAction = spamViolations24h >= SPAM_VIOLATION_LIMIT
                    ? "Tài khoản đã spam nhiều lần trong 24 giờ. Nên kiểm tra log và cân nhắc khóa tài khoản."
                    : "Tài khoản có nhiều bình luận vi phạm trong 7 ngày. Nên kiểm tra lịch sử trước khi xử lý.";

            return ReviewModerationUserRiskDTO.builder()
                    .userId(user.getUserId())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .isActive(user.getIsActive())
                    .spamViolations24h(spamViolations24h)
                    .reviewViolations7d(reviewViolations7d)
                    .reviewBlocked(isReviewBlocked())
                    .riskLevel(riskLevel)
                    .recommendedAction(recommendedAction)
                    .lastViolationType(lastViolationType)
                    .lastSeverity(lastSeverity)
                    .lastReason(lastReason)
                    .lastContentSnapshot(lastContentSnapshot)
                    .lastViolationAt(lastViolationAt)
                    .build();
        }
    }
}
