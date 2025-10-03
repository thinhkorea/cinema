package com.example.cinema.repository;

import com.example.cinema.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByRoom_RoomId(Long roomId);
}
