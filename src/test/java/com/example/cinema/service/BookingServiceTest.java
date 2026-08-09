package com.example.cinema.service;

import com.example.cinema.domain.Booking;
import com.example.cinema.dto.BookingResponseDTO;
import com.example.cinema.repository.BookingRepository;
import com.example.cinema.repository.CustomerRepository;
import com.example.cinema.repository.SeatRepository;
import com.example.cinema.repository.ShowtimeRepository;
import com.example.cinema.repository.SnackOrderItemRepository;
import com.example.cinema.repository.SnackOrderRepository;
import com.example.cinema.repository.StaffRepository;
import com.example.cinema.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private BookingRepository bookingRepo;
    @Mock private ShowtimeRepository showtimeRepo;
    @Mock private SeatRepository seatRepo;
    @Mock private StaffRepository staffRepo;
    @Mock private CustomerRepository customerRepo;
    @Mock private UserRepository userRepository;
    @Mock private TicketEmailService ticketEmailService;
    @Mock private PointService pointService;
    @Mock private SnackOrderService snackOrderService;
    @Mock private SnackOrderItemRepository snackOrderItemRepository;
    @Mock private SnackOrderRepository snackOrderRepository;

    @Test
    void findAllDTOUsesNewestBookingsFirst() {
        Booking older = booking(1L, LocalDateTime.of(2026, 8, 9, 10, 0));
        Booking newer = booking(2L, LocalDateTime.of(2026, 8, 9, 11, 0));

        when(bookingRepo.findAllByOrderByCreatedAtDescBookingIdDesc())
                .thenReturn(List.of(newer, older));

        List<BookingResponseDTO> result = service().findAllDTO();

        assertThat(result)
                .extracting(BookingResponseDTO::getBookingId)
                .containsExactly(2L, 1L);
        verify(bookingRepo).findAllByOrderByCreatedAtDescBookingIdDesc();
    }

    private BookingService service() {
        return new BookingService(
                bookingRepo,
                showtimeRepo,
                seatRepo,
                staffRepo,
                customerRepo,
                userRepository,
                ticketEmailService,
                pointService,
                snackOrderService,
                snackOrderItemRepository,
                snackOrderRepository);
    }

    private Booking booking(Long id, LocalDateTime createdAt) {
        Booking booking = new Booking();
        booking.setBookingId(id);
        booking.setCreatedAt(createdAt);
        booking.setStatus(Booking.Status.PAID);
        return booking;
    }
}
