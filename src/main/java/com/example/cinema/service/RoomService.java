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
        return roomRepository.findAll();
    }

    public Optional<Room> findById(Long id) {
        return roomRepository.findById(id);
    }

    public Room save(Room room) {
        return roomRepository.save(room);
    }

    @Transactional
    public void delete(Long id) {
        // Delete all seats associated with this room first
        seatRepository.deleteByRoom_RoomId(id);
        // Then delete the room
        roomRepository.deleteById(id);
    }

}
