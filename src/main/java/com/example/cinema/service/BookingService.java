package com.example.cinema.service;

import com.example.cinema.domain.*;
import com.example.cinema.dto.BookingResponse;
import com.example.cinema.dto.SoldTicketDTO;
import com.example.cinema.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private final BookingRepository bookingRepo;
    private final ShowtimeRepository showtimeRepo;
    private final SeatRepository seatRepo;
    private final TicketRepository ticketRepo;
    private final StaffRepository staffRepo;

    public BookingService(BookingRepository bookingRepo,
            ShowtimeRepository showtimeRepo,
            SeatRepository seatRepo, TicketRepository ticketRepo,
            StaffRepository staffRepo) {
        this.bookingRepo = bookingRepo;
        this.showtimeRepo = showtimeRepo;
        this.seatRepo = seatRepo;
        this.ticketRepo = ticketRepo;
        this.staffRepo = staffRepo;
    }

    // ==================== ADMIN / USER ====================
    public List<BookingResponse> findAllDTO() {
        return bookingRepo.findAll().stream()
                .map(b -> new BookingResponse(
                        b.getBookingId(),
                        (b.getCustomer() != null && b.getCustomer().getUser() != null)
                                ? b.getCustomer().getUser().getUsername()
                                : "-",
                        b.getShowtime().getMovie().getTitle(),
                        b.getShowtime().getRoom().getRoomName(),
                        b.getSeat().getSeatNumber(),
                        b.getShowtime().getStartTime().toString(),
                        b.getStatus().name(),
                        b.getCreatedAt()))
                .collect(Collectors.toList());
    }

    public List<Booking> findByUsername(String username) {
        return bookingRepo.findByCustomer_User_Username(username);
    }

    public List<Booking> findByShowtimeId(Long showtimeId) {
        return bookingRepo.findByShowtime_ShowtimeId(showtimeId);
    }

    public List<Booking> findByStatus(Booking.Status status) {
        return bookingRepo.findByStatus(status);
    }

    public List<Booking> findByStaffUsernameAndStatus(String username, Booking.Status status) {
        return bookingRepo.findBySoldByStaff_User_UsernameAndStatus(username, status);
    }

    @Transactional(readOnly = true) // Thêm @Transactional để đảm bảo các lazy-loading hoạt động
    public List<SoldTicketDTO> findSoldTicketsByStaffUsername(String username) {
        List<Booking> bookings = bookingRepo.findBySoldByStaff_User_UsernameAndStatus(username, Booking.Status.PAID);
        return bookings.stream()
                .map(SoldTicketDTO::new) // Sử dụng constructor để chuyển đổi
                .sorted(Comparator.comparing(SoldTicketDTO::getCreatedAt).reversed()) // Sắp xếp vé mới nhất lên đầu
                .collect(Collectors.toList());
    }

    // ==================== STAFF / VNPay ====================
    @Transactional
    public List<Booking> createMultiBooking(Long showtimeId, List<Long> seatIds, String txnRef, String staffUsername) {
        Showtime showtime = showtimeRepo.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Showtime not found"));

        // Tìm nhân viên bán vé dựa trên username
        Staff staff = staffRepo.findByUser_Username(staffUsername)
                .orElseThrow(() -> new IllegalArgumentException("Staff not found with username: " + staffUsername));

        List<Booking> list = new ArrayList<>();
        for (Long seatId : seatIds) {
            Seat seat = seatRepo.findById(seatId)
                    .orElseThrow(() -> new IllegalArgumentException("Seat not found: " + seatId));

            if (!seat.getRoom().getRoomId().equals(showtime.getRoom().getRoomId()))
                throw new IllegalStateException("Seat not in showtime room");

            if (bookingRepo.existsByShowtime_ShowtimeIdAndSeat_SeatId(showtimeId, seatId))
                throw new IllegalStateException("Seat already booked: " + seat.getSeatNumber());

            seat.setBooking(true);
            seatRepo.save(seat);

            Booking b = new Booking();
            b.setShowtime(showtime);
            b.setSeat(seat);
            b.setStatus(Booking.Status.PENDING);
            b.setTxnRef(txnRef);
            b.setSoldByStaff(staff); // Gán nhân viên đã bán vé này
            list.add(b);
        }
        return bookingRepo.saveAll(list);
    }

    @Transactional
    public void markPaidByTxn(String txnRef, String paymentMethod) {
        List<Booking> bookings = bookingRepo.findByTxnRef(txnRef);
        if (bookings.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy booking nào với mã giao dịch: " + txnRef);
        }

        for (Booking b : bookings) {
            b.setStatus(Booking.Status.PAID);
            b.setPaymentMethod(paymentMethod); // Gán phương thức thanh toán (CASH hoặc VNPAY)

            bookingRepo.save(b);
        }
    }

    // ==================== THỐNG KÊ ====================
    public List<Map<String, Object>> getMonthlyRevenue() {
        List<Object[]> results = bookingRepo.getMonthlyRevenue();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] r : results)
            list.add(Map.of("month", r[0], "revenue", r[1]));
        return list;
    }

    public List<Map<String, Object>> getMonthlyRevenueByYear(int year) {
        return bookingRepo.findMonthlyRevenueByYear(year).stream()
                .map(r -> Map.of("month", r[0], "revenue", r[1]))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getRevenueByMovie() {
        List<Object[]> results = bookingRepo.getRevenueByMovie();
        List<Map<String, Object>> movieRevenueList = new ArrayList<>();
        for (Object[] row : results) {
            movieRevenueList.add(Map.of("movieTitle", row[0], "revenue", row[1]));
        }
        return movieRevenueList;
    }

    public List<Map<String, Object>> getRevenueByStaff() {
        List<Object[]> results = bookingRepo.getRevenueByStaff();
        List<Map<String, Object>> staffRevenueList = new ArrayList<>();
        for (Object[] row : results) {
            staffRevenueList.add(Map.of(
                    "staffName", row[0],
                    "totalRevenue", row[1]));
        }
        return staffRevenueList;
    }

}
