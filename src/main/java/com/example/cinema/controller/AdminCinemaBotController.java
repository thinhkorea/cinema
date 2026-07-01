package com.example.cinema.controller;

import com.example.cinema.dto.CinemaBotEmbeddingRebuildResultDTO;
import com.example.cinema.dto.CinemaBotEmbeddingStatusDTO;
import com.example.cinema.service.CinemaRetrievalService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/bot")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCinemaBotController {

    private final CinemaRetrievalService retrievalService;

    public AdminCinemaBotController(CinemaRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @GetMapping("/embeddings/status")
    public ResponseEntity<CinemaBotEmbeddingStatusDTO> getEmbeddingStatus() {
        return ResponseEntity.ok(retrievalService.getEmbeddingStatus());
    }

    @PostMapping("/embeddings/rebuild")
    public ResponseEntity<CinemaBotEmbeddingRebuildResultDTO> rebuildEmbeddings(
            @RequestParam(defaultValue = "false") boolean force
    ) {
        return ResponseEntity.ok(retrievalService.rebuildEmbeddings(force));
    }
}
