package com.example.cinema.service;

import com.example.cinema.domain.Booking;
import com.example.cinema.domain.Movie;
import com.example.cinema.domain.MovieReview;
import com.example.cinema.domain.User;
import com.example.cinema.domain.UserViolationLog;
import com.example.cinema.dto.MovieReviewRequestDTO;
import com.example.cinema.dto.MovieReviewResponseDTO;
import com.example.cinema.repository.BookingRepository;
import com.example.cinema.repository.MovieRepository;
import com.example.cinema.repository.MovieReviewRepository;
import com.example.cinema.repository.UserViolationLogRepository;
import com.example.cinema.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MovieReviewService {

    private static final int REVIEW_WINDOW_DAYS = 7;

    private final MovieReviewRepository reviewRepo;
    private final MovieRepository movieRepo;
    private final UserRepository userRepo;
    private final BookingRepository bookingRepo;
    private final ReviewModerationService reviewModerationService;
    private final UserViolationLogRepository violationLogRepo;

    public MovieReviewService(
            MovieReviewRepository reviewRepo,
            MovieRepository movieRepo,
            UserRepository userRepo,
            BookingRepository bookingRepo,
            ReviewModerationService reviewModerationService,
            UserViolationLogRepository violationLogRepo) {
        this.reviewRepo = reviewRepo;
        this.movieRepo = movieRepo;
        this.userRepo = userRepo;
        this.bookingRepo = bookingRepo;
        this.reviewModerationService = reviewModerationService;
        this.violationLogRepo = violationLogRepo;
    }

    @Transactional(readOnly = true)
    public List<MovieReviewResponseDTO> getByMovieId(Long movieId) {
        return getVisibleReviews(movieId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSummary(Long movieId) {
        List<MovieReview> visibleReviews = getVisibleReviews(movieId);
        long reviewCount = visibleReviews.size();
        double avg = visibleReviews.stream()
                .map(MovieReview::getRating)
                .filter(rating -> rating != null)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        Map<String, Object> summary = new HashMap<>();
        summary.put("reviewCount", reviewCount);
        summary.put("averageRating", Math.round(avg * 10.0) / 10.0);
        return summary;
    }

    @Transactional
    public MovieReviewResponseDTO createOrUpdate(Long movieId, String username, MovieReviewRequestDTO request) {
        Movie movie = movieRepo.findById(movieId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phim"));

        User user = getCustomerUser(username);
        validateReviewEligibility(movieId, user);

        Optional<MovieReview> existingReview = reviewRepo.findByMovie_MovieIdAndUser_UserId(movieId, user.getUserId());
        if (existingReview.isPresent()) {
            MovieReview review = existingReview.get();
            if (review.getModerationStatus() == MovieReview.ModerationStatus.REJECTED) {
                ReviewModerationService.ModerationResult moderation = applyReviewContent(review, request);
                MovieReview saved = reviewRepo.save(review);
                if (moderation.flagged()) {
                    logViolation(user, saved, saved.getComment(), moderation);
                }
                return toResponse(saved);
            }

            if (review.getModerationStatus() == MovieReview.ModerationStatus.FLAGGED
                    || review.getModerationStatus() == MovieReview.ModerationStatus.PENDING_REVIEW) {
                throw new IllegalArgumentException("Đánh giá của bạn đang chờ kiểm duyệt. Vui lòng đợi admin xử lý trước khi gửi lại.");
            }

            throw new IllegalArgumentException("Bạn chỉ được đánh giá phim này 1 lần duy nhất");
        }

        MovieReview review = new MovieReview();

        review.setMovie(movie);
        review.setUser(user);
        ReviewModerationService.ModerationResult moderation = applyReviewContent(review, request);

        MovieReview saved = reviewRepo.save(review);
        if (moderation.flagged()) {
            logViolation(user, saved, saved.getComment(), moderation);
        }
        return toResponse(saved);
    }

    @Transactional
    public MovieReviewResponseDTO updateReview(Long movieId, Long reviewId, String username, MovieReviewRequestDTO request) {
        User user = getCustomerUser(username);
        validateReviewEligibility(movieId, user);
        MovieReview review = getOwnedReview(movieId, reviewId, user);

        ReviewModerationService.ModerationResult moderation = applyReviewContent(review, request);
        MovieReview saved = reviewRepo.save(review);
        if (moderation.flagged()) {
            logViolation(user, saved, saved.getComment(), moderation);
        }
        return toResponse(saved);
    }

    @Transactional
    public void deleteReview(Long movieId, Long reviewId, String username) {
        User user = getCustomerUser(username);
        MovieReview review = getOwnedReview(movieId, reviewId, user);
        violationLogRepo.deleteByReview_ReviewId(review.getReviewId());
        reviewRepo.delete(review);
    }

    private User getCustomerUser(String username) {
        User user = userRepo.findByEmailOrPhone(username, username);
        if (user == null) {
            throw new IllegalArgumentException("Không tìm thấy người dùng");
        }

        if (user.getRole() != User.Role.CUSTOMER) {
            throw new IllegalArgumentException("Chỉ khách hàng mới được đánh giá phim");
        }

        return user;
    }

    private void validateReviewEligibility(Long movieId, User user) {
        String userEmail = user.getEmail();

        boolean hasPaidBooking = bookingRepo.existsByCustomer_User_EmailAndShowtime_Movie_MovieIdAndStatus(
                userEmail,
                movieId,
                Booking.Status.PAID);

        if (!hasPaidBooking) {
            boolean hasCancelled = bookingRepo.existsByCustomer_User_EmailAndShowtime_Movie_MovieIdAndStatus(
                    userEmail,
                    movieId,
                    Booking.Status.CANCELLED);

            if (hasCancelled) {
                throw new IllegalArgumentException("Vé đã hủy không được đánh giá");
            }

            throw new IllegalArgumentException("Bạn cần mua và thanh toán vé xem phim này trước khi đánh giá");
        }

        LocalDateTime currentTime = LocalDateTime.now();
        LocalDateTime reviewWindowStart = currentTime.minusDays(REVIEW_WINDOW_DAYS);
        boolean hasEligibleBooking = bookingRepo.existsEligibleReviewBooking(
                userEmail,
                movieId,
                Booking.Status.PAID,
                currentTime,
                reviewWindowStart);

        if (!hasEligibleBooking) {
            throw new IllegalArgumentException("Bạn chỉ có thể đánh giá sau khi suất chiếu đã kết thúc và trong vòng 7 ngày kể từ lúc suất chiếu kết thúc");
        }
    }

    private MovieReview getOwnedReview(Long movieId, Long reviewId, User user) {
        MovieReview review = reviewRepo.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đánh giá"));

        Long reviewMovieId = review.getMovie() != null ? review.getMovie().getMovieId() : null;
        Long reviewUserId = review.getUser() != null ? review.getUser().getUserId() : null;
        if (!movieId.equals(reviewMovieId) || !user.getUserId().equals(reviewUserId)) {
            throw new IllegalArgumentException("Bạn chỉ có thể chỉnh sửa hoặc xóa đánh giá của chính mình");
        }

        return review;
    }

    private ReviewModerationService.ModerationResult applyReviewContent(MovieReview review, MovieReviewRequestDTO request) {
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new IllegalArgumentException("Đánh giá phải từ 1 đến 5 sao");
        }

        String comment = request.getComment() == null ? "" : request.getComment().trim();
        if (comment.length() > 1000) {
            throw new IllegalArgumentException("Nội dung review tối đa 1000 ký tự");
        }

        review.setRating(request.getRating());
        review.setComment(comment);

        ReviewModerationService.ModerationResult moderation = reviewModerationService.moderateMovieReview(comment);
        applyModerationResult(review, moderation);
        return moderation;
    }

    private List<MovieReview> getVisibleReviews(Long movieId) {
        return reviewRepo.findByMovie_MovieIdOrderByCreatedAtDesc(movieId)
                .stream()
                .filter(this::isVisibleReview)
                .toList();
    }

    private boolean isVisibleReview(MovieReview review) {
        if (review.getModerationStatus() == null) {
            return !reviewModerationService.looksUnsafeByRules(review.getComment());
        }
        return review.getModerationStatus() == MovieReview.ModerationStatus.APPROVED
                && !Boolean.TRUE.equals(review.getFlagged());
    }

    private void applyModerationResult(MovieReview review, ReviewModerationService.ModerationResult moderation) {
        review.setModerationStatus(moderation.flagged()
                ? MovieReview.ModerationStatus.FLAGGED
                : MovieReview.ModerationStatus.APPROVED);
        review.setFlagged(moderation.flagged());
        review.setViolationType(moderation.flagged() ? truncate(moderation.violationType(), 50) : null);
        review.setViolationSeverity(moderation.flagged() ? truncate(moderation.severity(), 30) : null);
        review.setViolationReason(moderation.flagged() ? truncate(moderation.reason(), 1000) : null);
        review.setModerationProvider(truncate(moderation.provider(), 50));
        review.setModeratedAt(LocalDateTime.now());
    }

    private void logViolation(
            User user,
            MovieReview review,
            String comment,
            ReviewModerationService.ModerationResult moderation) {
        UserViolationLog log = new UserViolationLog();
        log.setUser(user);
        log.setReview(review);
        log.setSourceType("MOVIE_REVIEW");
        log.setViolationType(truncate(moderation.violationType(), 50));
        log.setSeverity(truncate(moderation.severity(), 30));
        log.setReason(truncate(moderation.reason(), 1000));
        log.setContentSnapshot(truncate(comment, 1000));
        log.setModerationProvider(truncate(moderation.provider(), 50));
        violationLogRepo.save(log);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private MovieReviewResponseDTO toResponse(MovieReview review) {
        User u = review.getUser();
        Movie movie = review.getMovie();
        return MovieReviewResponseDTO.builder()
                .reviewId(review.getReviewId())
                .movieId(movie != null ? movie.getMovieId() : null)
                .movieTitle(movie != null ? movie.getTitle() : null)
                .userId(u != null ? u.getUserId() : null)
                .rating(review.getRating())
                .comment(review.getComment())
                .username(u != null ? u.getEmail() : "")
                .fullName(u != null && u.getFullName() != null ? u.getFullName() : (u != null ? u.getEmail() : ""))
                .moderationStatus(review.getModerationStatus() != null ? review.getModerationStatus().name() : null)
                .flagged(review.getFlagged())
                .violationType(review.getViolationType())
                .violationSeverity(review.getViolationSeverity())
                .violationReason(review.getViolationReason())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
