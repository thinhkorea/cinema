package com.example.cinema.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.cinema.domain.Room;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
}
