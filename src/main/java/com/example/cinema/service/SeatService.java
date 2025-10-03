package com.example.cinema.service;

import com.example.cinema.domain.Seat;
import com.example.cinema.repository.SeatRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SeatService {
    private final SeatRepository seatRepository;

    public SeatService(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    public List<Seat> findAll() {
        return seatRepository.findAll();
    }

    public Optional<Seat> findById(Long id) {
        return seatRepository.findById(id);
    }

    public List<Seat> findByRoom(Long roomId) {
        return seatRepository.findByRoom_RoomId(roomId);
    }

    public Seat save(Seat seat) {
        return seatRepository.save(seat);
    }

    public void delete(Long id) {
        seatRepository.deleteById(id);
    }
}
