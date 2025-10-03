package com.example.cinema.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.cinema.domain.Ticket;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    // Ví dụ: thống kê số vé theo suất chiếu
    Long countByShowtime_ShowtimeId(Long showtimeId);
}
