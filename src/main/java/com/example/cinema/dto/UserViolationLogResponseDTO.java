package com.example.cinema.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserViolationLogResponseDTO {
    private Long violationLogId;
    private Long userId;
    private String username;
    private String fullName;
    private Long reviewId;
    private Long movieId;
    private String movieTitle;
    private String sourceType;
    private String violationType;
    private String severity;
    private String reason;
    private String contentSnapshot;
    private String moderationProvider;
    private LocalDateTime createdAt;
}
