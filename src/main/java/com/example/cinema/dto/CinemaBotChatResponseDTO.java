package com.example.cinema.dto;

import java.util.List;

public record CinemaBotChatResponseDTO(
        String answer,
        List<CinemaBotShowtimeSuggestionDTO> showtimeSuggestions
) {
    public CinemaBotChatResponseDTO(String answer) {
        this(answer, List.of());
    }
}
