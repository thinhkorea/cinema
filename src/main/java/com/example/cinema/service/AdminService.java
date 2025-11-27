package com.example.cinema.service;

import com.example.cinema.dto.BookingResponse;
import com.example.cinema.repository.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

        private final MovieRepository movieRepo;
        private final RoomRepository roomRepo;
        private final ShowtimeRepository showtimeRepo;
        private final BookingRepository bookingRepo;

        public AdminService(MovieRepository movieRepo,
                        RoomRepository roomRepo,
                        ShowtimeRepository showtimeRepo,
                        BookingRepository bookingRepo) {
                this.movieRepo = movieRepo;
                this.roomRepo = roomRepo;
                this.showtimeRepo = showtimeRepo;
                this.bookingRepo = bookingRepo;
        }

        // API thống kê dashboard
        public Map<String, Long> getDashboardStats() {
                return Map.of(
                                "movies", movieRepo.count(),
                                "rooms", roomRepo.count(),
                                "showtimes", showtimeRepo.count(),
                                "bookings", bookingRepo.count());
        }

        // API danh sách booking (DTO)
        public List<BookingResponse> getAllBookings() {
                return bookingRepo.findAll()
                                .stream()
                                .map(b -> new BookingResponse(
                                                b.getBookingId(),
                                                // Lấy username từ Customer -> User
                                                (b.getCustomer() != null && b.getCustomer().getUser() != null)
                                                                ? b.getCustomer().getUser().getUsername()
                                                                : "-",
                                                // Lấy staffName từ Staff -> User
                                                (b.getSoldByStaff() != null && b.getSoldByStaff().getUser() != null)
                                                                ? b.getSoldByStaff().getUser().getFullName()
                                                                : null,
                                                (b.getShowtime() != null && b.getShowtime().getMovie() != null)
                                                                ? b.getShowtime().getMovie().getTitle()
                                                                : "-",
                                                (b.getShowtime() != null && b.getShowtime().getRoom() != null)
                                                                ? b.getShowtime().getRoom().getRoomName()
                                                                : "-",
                                                b.getSeat() != null ? b.getSeat().getSeatNumber() : "-",
                                                (b.getShowtime() != null && b.getShowtime().getStartTime() != null)
                                                                ? b.getShowtime().getStartTime().toString()
                                                                : "-",
                                                b.getStatus() != null ? b.getStatus().name() : "-",
                                                b.getCreatedAt()))
                                .collect(Collectors.toList());
        }
}
