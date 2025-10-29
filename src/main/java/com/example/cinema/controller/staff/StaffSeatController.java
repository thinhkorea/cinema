package com.example.cinema.controller.staff;

import com.example.cinema.dto.SeatStatusDTO;
import com.example.cinema.service.SeatService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/staff/showtimes")
public class StaffSeatController {

    private final SeatService seatService;

    public StaffSeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    @GetMapping("/{showtimeId}/seats")
    public List<SeatStatusDTO> getSeatsByShowtime(@PathVariable Long showtimeId) {
        return seatService.getSeatsByShowtime(showtimeId);
    }
}
