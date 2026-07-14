package com.example.cinema.dto;

import com.example.cinema.domain.Booking;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoldTicketDTO {
    private Long bookingId;
    private Long movieId;
    private String movieTitle;
    private String roomName;
    private String seatNumber;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String paymentMethod;
    private String status;
    private LocalDateTime createdAt;
    private Double total;
    private String moviePoster;
    private String txnRef;
    private boolean printed;
    private boolean snacksFulfilled;
    private Integer pointsUsed; // Điểm tích lũy được dùng

    // Constructor để chuyển đổi từ Booking Entity sang DTO.
    public SoldTicketDTO(Booking booking) {
        this.bookingId = booking.getBookingId();
        if (booking.getShowtime() != null) {
            if (booking.getShowtime().getMovie() != null) {
                this.movieId = booking.getShowtime().getMovie().getMovieId();
                this.movieTitle = booking.getShowtime().getMovie().getTitle();
                this.moviePoster = booking.getShowtime().getMovie().getPosterUrl();
            }
            if (booking.getShowtime().getRoom() != null) {
                this.roomName = booking.getShowtime().getRoom().getRoomName();
            }
            this.startTime = booking.getShowtime().getStartTime();
            this.endTime = booking.getShowtime().getEndTime();
        }
        if (booking.getSeat() != null) {
            this.seatNumber = booking.getSeat().getSeatNumber();
        }
        this.paymentMethod = booking.getPaymentMethod();
        if (booking.getStatus() != null) {
            this.status = booking.getStatus().name();
        }
        if (booking.getCreatedAt() != null) {
            this.createdAt = booking.getCreatedAt();
        }
        this.total = booking.getTotal();
        this.txnRef = booking.getTxnRef();
        this.printed = booking.isPrinted();
        this.snacksFulfilled = booking.isSnacksFulfilled();
        this.pointsUsed = booking.getPointsUsed() != null ? booking.getPointsUsed() : 0;
    }

    public static SoldTicketDTO fromBooking(Booking booking) {
        return new SoldTicketDTO(booking);
    }
}
