package com.example.cinema.controller;

import com.example.cinema.dto.MovieReviewRequest;
import com.example.cinema.service.MovieReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/movies/{movieId}/reviews")
public class MovieReviewController {

    private final MovieReviewService reviewService;

    public MovieReviewController(MovieReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ResponseEntity<?> getReviews(@PathVariable Long movieId) {
        return ResponseEntity.ok(Map.of(
                "summary", reviewService.getSummary(movieId),
                "reviews", reviewService.getByMovieId(movieId)
        ));
    }

    @PostMapping
    public ResponseEntity<?> createReview(
            @PathVariable Long movieId,
            @RequestBody MovieReviewRequest request,
            Authentication authentication) {
        try {
            if (authentication == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Bạn cần đăng nhập để đánh giá phim"));
            }
            return ResponseEntity.ok(reviewService.createOrUpdate(movieId, authentication.getName(), request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
