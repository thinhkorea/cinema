package com.example.cinema.service;

import com.example.cinema.domain.Movie;
import com.example.cinema.domain.MovieReview;
import com.example.cinema.domain.User;
import com.example.cinema.dto.MovieReviewRequest;
import com.example.cinema.dto.MovieReviewResponse;
import com.example.cinema.repository.BookingRepository;
import com.example.cinema.repository.MovieRepository;
import com.example.cinema.repository.MovieReviewRepository;
import com.example.cinema.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MovieReviewService {

    private final MovieReviewRepository reviewRepo;
    private final MovieRepository movieRepo;
    private final UserRepository userRepo;
    private final BookingRepository bookingRepo;

    public MovieReviewService(
            MovieReviewRepository reviewRepo,
            MovieRepository movieRepo,
            UserRepository userRepo,
            BookingRepository bookingRepo) {
        this.reviewRepo = reviewRepo;
        this.movieRepo = movieRepo;
        this.userRepo = userRepo;
        this.bookingRepo = bookingRepo;
    }

    @Transactional(readOnly = true)
    public List<MovieReviewResponse> getByMovieId(Long movieId) {
        return reviewRepo.findByMovie_MovieIdOrderByCreatedAtDesc(movieId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSummary(Long movieId) {
        long reviewCount = reviewRepo.countByMovie_MovieId(movieId);
        Double avg = reviewRepo.findAverageRatingByMovieId(movieId);

        Map<String, Object> summary = new HashMap<>();
        summary.put("reviewCount", reviewCount);
        summary.put("averageRating", avg == null ? 0.0 : Math.round(avg * 10.0) / 10.0);
        return summary;
    }

    @Transactional
    public MovieReviewResponse createOrUpdate(Long movieId, String username, MovieReviewRequest request) {
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new IllegalArgumentException("Đánh giá phải từ 1 đến 5 sao");
        }

        String comment = request.getComment() == null ? "" : request.getComment().trim();
        if (comment.length() > 1000) {
            throw new IllegalArgumentException("Nội dung review tối đa 1000 ký tự");
        }

        Movie movie = movieRepo.findById(movieId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phim"));

        User user = userRepo.findByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("Không tìm thấy người dùng");
        }

        boolean hasPaidBooking = bookingRepo.existsByCustomer_User_UsernameAndShowtime_Movie_MovieIdAndStatus(
                username,
                movieId,
                com.example.cinema.domain.Booking.Status.PAID);

        if (!hasPaidBooking) {
            throw new IllegalArgumentException("Bạn cần mua và thanh toán vé xem phim này trước khi đánh giá");
        }

        // Kiểm tra xem có ít nhất 1 suất chiếu đã hoàn thành
        boolean hasCompletedShowtime = bookingRepo.existsCompletedShowtimeBooking(
                username,
                movieId,
                com.example.cinema.domain.Booking.Status.PAID,
                java.time.LocalDateTime.now());

        if (!hasCompletedShowtime) {
            throw new IllegalArgumentException("Bạn chỉ có thể đánh giá sau khi suất chiếu đã kết thúc");
        }

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       boolean alreadyReviewed = reviewRepo.findByMovie_MovieIdAndUser_UserId(movieId, user.getUserId()).isPresent();
        if (alreadyReviewed) {
            throw new IllegalArgumentException("Bạn chỉ được đánh giá phim này 1 lần duy nhất");
        }

        MovieReview review = new MovieReview();

        review.setMovie(movie);
        review.setUser(user);
        review.setRating(request.getRating());
        review.setComment(comment);

        MovieReview saved = reviewRepo.save(review);
        return toResponse(saved);
    }

    private MovieReviewResponse toResponse(MovieReview review) {
        User u = review.getUser();
        return new MovieReviewResponse(
                review.getReviewId(),
                review.getRating(),
                review.getComment(),
                u != null ? u.getUsername() : "",
                u != null && u.getFullName() != null ? u.getFullName() : (u != null ? u.getUsername() : ""),
                review.getCreatedAt());
    }
}
