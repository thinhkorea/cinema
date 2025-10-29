package com.example.cinema.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.example.cinema.domain.Ticket;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // Đếm số vé của một suất chiếu
    Long countByShowtime_ShowtimeId(Long showtimeId);

    // Lấy danh sách vé theo nhân viên bán
    List<Ticket> findBySoldBy_StaffId(Long staffId);

    List<Ticket> findAllByOrderBySoldAtDesc();

    // Thống kê doanh thu theo tháng (dành cho dashboard)
    @Query("""
                SELECT
                    FUNCTION('DATE_FORMAT', t.soldAt, '%Y-%m') AS month,
                    SUM(t.price) AS totalRevenue
                FROM Ticket t
                GROUP BY FUNCTION('DATE_FORMAT', t.soldAt, '%Y-%m')
                ORDER BY month
            """)
    List<Object[]> getMonthlyRevenue();

    // Lấy danh sách vé theo username của nhân viên
    @Query("""
                SELECT t
                FROM Ticket t
                WHERE t.soldBy.user.username = :username
            """)
    List<Ticket> findBySoldByUsername(@Param("username") String username);

    @Query("""
                SELECT CONCAT(s.user.username, ' (', s.position, ')') AS staffName,
                       SUM(t.price) AS totalRevenue
                FROM Ticket t
                JOIN t.soldBy s
                GROUP BY s.user.username, s.position
                ORDER BY totalRevenue DESC
            """)
    List<Object[]> getRevenueByStaff();

}
