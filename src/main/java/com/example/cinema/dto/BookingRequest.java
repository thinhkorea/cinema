package com.example.cinema.dto;

import java.util.List;

public class BookingRequest {
    private Long showtimeId;
    private List<Long> seatIds;
    private String staffUsername; // Thêm trường này

    // Getters
    public Long getShowtimeId() {
        return showtimeId;
    }

    public List<Long> getSeatIds() {
        return seatIds;
    }

    public String getStaffUsername() {
        return staffUsername;
    }

    // Setters
    public void setShowtimeId(Long showtimeId) {
        this.showtimeId = showtimeId;
    }

    public void setSeatIds(List<Long> seatIds) {
        this.seatIds = seatIds;
    }

    public void setStaffUsername(String staffUsername) {
        this.staffUsername = staffUsername;
    }
}