package com.example.cinema.repository;

import com.example.cinema.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    // Ví dụ: thống kê số vé theo suất chiếu
    Long countByShowtime_ShowtimeId(Long showtimeId);
}
