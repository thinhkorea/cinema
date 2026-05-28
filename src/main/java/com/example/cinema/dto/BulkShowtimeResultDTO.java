package com.example.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkShowtimeResultDTO {
    @Builder.Default
    private int createdCount = 0;

    @Builder.Default
    private int skippedCount = 0;

    @Builder.Default
    private int remainingCreatableCount = 0;

    private Integer limitApplied;

    @Builder.Default
    private boolean limitReached = false;

    @Builder.Default
    private List<ShowtimeRequestDTO> createdShowtimes = new ArrayList<>();

    @Builder.Default
    private List<String> skippedMessages = new ArrayList<>();
}
