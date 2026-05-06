package com.example.cinema.service;

import com.example.cinema.dto.BookingResponseDTO;
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
        public List<BookingResponseDTO> getAllBookings() {
                return bookingRepo.findAll()
                                .stream()
                                .map(b -> BookingResponseDTO.builder()
                                                                .bookingId(b.getBookingId())
                                                                .username((b.getCustomer() != null && b.getCustomer().getUser() != null)
                                                                                ? b.getCustomer().getUser().getEmail()
                                                                                : "-")
                                                                .soldByStaff((b.getSoldByStaff() != null && b.getSoldByStaff().getUser() != null)
                                                                                ? b.getSoldByStaff().getUser().getFullName()
                                                                                : null)
                                                                .movieTitle((b.getShowtime() != null && b.getShowtime().getMovie() != null)
                                                                                ? b.getShowtime().getMovie().getTitle()
                                                                                : "-")
                                                                .roomName((b.getShowtime() != null && b.getShowtime().getRoom() != null)
                                                                                ? b.getShowtime().getRoom().getRoomName()
                                                                                : "-")
                                                                .seatNumber(b.getSeat() != null ? b.getSeat().getSeatNumber() : "-")
                                                                .showtime((b.getShowtime() != null && b.getShowtime().getStartTime() != null)
                                                                                ? b.getShowtime().getStartTime().toString()
                                                                                : "-")
                                                                .status(b.getStatus() != null ? b.getStatus().name() : "-")
                                                                .createdAt(b.getCreatedAt())
                                                                .build())
                                .collect(Collectors.toList());
        }
}
