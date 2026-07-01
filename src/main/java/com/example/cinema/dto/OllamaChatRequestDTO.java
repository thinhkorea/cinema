package com.example.cinema.dto;

import java.util.List;

public record OllamaChatRequestDTO(
        String model,
        List<OllamaMessageDTO> messages,
        boolean stream
) {
}
