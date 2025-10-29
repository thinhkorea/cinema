package com.example.cinema.service;

import com.example.cinema.domain.*;
import com.example.cinema.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TicketService {

    private final TicketRepository ticketRepo;
    private final StaffRepository staffRepo;
    private final ShowtimeRepository showtimeRepo;

    public TicketService(TicketRepository ticketRepo,
            StaffRepository staffRepo,
            ShowtimeRepository showtimeRepo) {
        this.ticketRepo = ticketRepo;
        this.staffRepo = staffRepo;
        this.showtimeRepo = showtimeRepo;
    }

    // Tạo vé mới (bán vé)
    @Transactional
    public Ticket createTicket(String username, Long showtimeId, String seatNumber, Double price) {
        // 1️⃣ Tìm nhân viên bán vé thông qua username
        Staff staff = staffRepo.findByUser_Username(username)
                .orElseThrow(() -> new IllegalArgumentException("Staff not found for username: " + username));

        // 2️⃣ Kiểm tra suất chiếu hợp lệ
        Showtime showtime = showtimeRepo.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Showtime not found: " + showtimeId));

        // 3️⃣ Tạo vé mới
        Ticket ticket = new Ticket();
        ticket.setShowtime(showtime);
        ticket.setSeatNumber(seatNumber);
        ticket.setPrice(price != null ? price : showtime.getPrice());
        ticket.setSoldBy(staff);

        return ticketRepo.save(ticket);
    }

    // Lấy danh sách vé theo nhân viên bán
    public List<Ticket> findByStaffId(Long staffId) {
        return ticketRepo.findBySoldBy_StaffId(staffId);
    }

    // Lấy danh sách vé theo username của nhân viên
    public List<Ticket> findByStaffUsername(String username) {
        return ticketRepo.findBySoldByUsername(username);
    }

    // Đếm số vé của một suất chiếu
    public Long countByShowtime(Long showtimeId) {
        return ticketRepo.countByShowtime_ShowtimeId(showtimeId);
    }

    // Thống kê doanh thu theo nhân viên
    public List<Map<String, Object>> getRevenueByStaff() {
        List<Object[]> results = ticketRepo.getRevenueByStaff();
        List<Map<String, Object>> revenueList = new ArrayList<>();

        for (Object[] row : results) {
            Map<String, Object> map = new HashMap<>();
            map.put("staffName", row[0]);
            map.put("totalTickets", row[1]);
            map.put("totalRevenue", row[2]);
            revenueList.add(map);
        }
        return revenueList;
    }

    // Thống kê doanh thu theo tháng (biểu đồ)
    public List<Map<String, Object>> getMonthlyRevenue() {
        List<Object[]> results = ticketRepo.getMonthlyRevenue();
        List<Map<String, Object>> revenueList = new ArrayList<>();

        for (Object[] row : results) {
            Map<String, Object> map = new HashMap<>();
            map.put("month", row[0]);
            map.put("totalRevenue", row[1]);
            revenueList.add(map);
        }
        return revenueList;
    }

    // Xóa vé (nếu cần quản lý admin)
    @Transactional
    public void deleteTicket(Long ticketId) {
        ticketRepo.deleteById(ticketId);
    }
}
