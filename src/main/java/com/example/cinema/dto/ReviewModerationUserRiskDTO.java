package com.example.cinema.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReviewModerationUserRiskDTO {
    private Long userId;
    private String email;
    private String fullName;
    private Boolean isActive;
    private Long spamViolations24h;
    private Long reviewViolations7d;
    private Boolean reviewBlocked;
    private String riskLevel;
    private String recommendedAction;
    private String lastViolationType;
    private String lastSeverity;
    private String lastReason;
    private String lastContentSnapshot;
    private LocalDateTime lastViolationAt;
}
