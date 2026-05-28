package com.example.cinema.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cinema.domain.Room;
import com.example.cinema.repository.RoomRepository;
import com.example.cinema.repository.SeatRepository;

@Service
public class RoomService {
    private final RoomRepository roomRepository;
    private final SeatRepository seatRepository;

    public RoomService(RoomRepository roomRepository, SeatRepository seatRepository) {
        this.roomRepository = roomRepository;
        this.seatRepository = seatRepository;
    }

    public List<Room> findAll() {
        return roomRepository.findByActiveTrue();
    }

    public Optional<Room> findById(Long id) {
        return roomRepository.findById(id);
    }

    public Room save(Room room) {
        if (room.getActive() == null) {
            room.setActive(true);
        }
        return roomRepository.save(room);
    }

    @Transactional
    public void delete(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));
        room.setActive(false);
        roomRepository.save(room);

        List<com.example.cinema.domain.Seat> seats = seatRepository.findByRoom_RoomId(id);
        seats.forEach(seat -> seat.setActive(false));
        seatRepository.saveAll(seats);
    }

}
