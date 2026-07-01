package com.example.cinema.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OllamaChatResponseDTO(
        String model,
        OllamaMessageDTO message,
        boolean done,
        @JsonProperty("done_reason") String doneReason
) {
    public String content() {
        return message != null ? message.content() : null;
    }
}
