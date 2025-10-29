package com.example.cinema.controller.staff;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cinema.repository.TicketRepository;

@RestController
@RequestMapping("/api/staff/tickets")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class StaffTicketController {

    private TicketRepository ticketRepo = null;

    public StaffTicketController(TicketRepository ticketRepo) {
        this.ticketRepo = ticketRepo;
    }

    @GetMapping
    public ResponseEntity<?> getAllTickets() {
        return ResponseEntity.ok(ticketRepo.findAllByOrderBySoldAtDesc());
    }
}
