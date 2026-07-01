package com.example.cinema.controller;

import com.example.cinema.dto.CinemaBotChatRequestDTO;
import com.example.cinema.dto.CinemaBotChatResponseDTO;
import com.example.cinema.dto.CinemaBotShowtimeSuggestionDTO;
import com.example.cinema.service.CinemaBotService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bot")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class CinemaBotController {

    private final CinemaBotService cinemaBotService;

    public CinemaBotController(CinemaBotService cinemaBotService) {
        this.cinemaBotService = cinemaBotService;
    }

    @PostMapping(
            value = "/chat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> chat(@RequestBody CinemaBotChatRequestDTO request) {
        try {
            String question = request != null ? request.question() : null;
            String conversationId = request != null ? request.conversationId() : null;
            String answer = cinemaBotService.askBot(question, conversationId);
            List<CinemaBotShowtimeSuggestionDTO> showtimeSuggestions = cinemaBotService.suggestShowtimes(question);
            return ResponseEntity.ok(new CinemaBotChatResponseDTO(answer, showtimeSuggestions));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Cannot process chatbot request"));
        }
    }
}
