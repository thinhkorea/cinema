package com.example.cinema.service;

import com.example.cinema.domain.Booking;
import com.example.cinema.domain.BookingSnack;
import com.example.cinema.domain.Snack;
import com.example.cinema.dto.AddSnacksRequest;
import com.example.cinema.dto.BookingSnackDTO;
import com.example.cinema.dto.SnackDTO;
import com.example.cinema.dto.SnackItemRequest;
import com.example.cinema.repository.BookingRepository;
import com.example.cinema.repository.BookingSnackRepository;
import com.example.cinema.repository.SnackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SnackService {

    private final SnackRepository snackRepository;
    private final BookingSnackRepository bookingSnackRepository;
    private final BookingRepository bookingRepository;

    /**
     * Lấy tất cả snacks available
     */
    public List<SnackDTO> getAllAvailableSnacks() {
        return snackRepository.findByAvailableTrue().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Lấy snacks theo category
     */
    public List<SnackDTO> getSnacksByCategory(Snack.SnackCategory category) {
        return snackRepository.findByCategoryAndAvailableTrue(category).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Lấy snack theo ID
     */
    public Snack getSnackById(Long snackId) {
        return snackRepository.findById(snackId)
                .orElseThrow(() -> new RuntimeException("Snack not found with id: " + snackId));
    }

    /**
     * Tạo snack mới (Admin)
     */
    @Transactional
    public Snack createSnack(Snack snack) {
        return snackRepository.save(snack);
    }

    /**
     * Cập nhật snack (Admin)
     */
    @Transactional
    public Snack updateSnack(Long snackId, Snack snackDetails) {
        Snack snack = getSnackById(snackId);
        snack.setSnackName(snackDetails.getSnackName());
        snack.setDescription(snackDetails.getDescription());
        snack.setPrice(snackDetails.getPrice());
        snack.setImageUrl(snackDetails.getImageUrl());
        snack.setStock(snackDetails.getStock());
        snack.setCategory(snackDetails.getCategory());
        snack.setAvailable(snackDetails.getAvailable());
        return snackRepository.save(snack);
    }

    /**
     * Xóa snack (Admin)
     */
    @Transactional
    public void deleteSnack(Long snackId) {
        snackRepository.deleteById(snackId);
    }

    /**
     * Thêm snacks vào các bookings của một transaction
     * Sử dụng khi customer đã chọn snacks và cần lưu vào database
     */
    @Transactional
    public Map<String, Object> addSnacksToBookings(AddSnacksRequest request) {
        String txnRef = request.getTxnRef();
        List<SnackItemRequest> snackItems = request.getSnacks();

        // Tìm tất cả bookings trong transaction này
        List<Booking> bookings = bookingRepository.findByTxnRef(txnRef);
        if (bookings.isEmpty()) {
            throw new RuntimeException("No bookings found with txnRef: " + txnRef);
        }

        // Tính tổng tiền snacks
        double totalSnacksCost = 0.0;

        // Thêm snacks vào từng booking (hoặc chỉ booking đầu tiên nếu muốn group)
        // Ở đây tôi thêm vào booking đầu tiên trong txnRef
        Booking firstBooking = bookings.get(0);

        for (SnackItemRequest item : snackItems) {
            if (item.getQuantity() <= 0) {
                continue;
            }

            Snack snack = getSnackById(item.getSnackId());

            // Kiểm tra tồn kho
            if (snack.getStock() != null && snack.getStock() < item.getQuantity()) {
                throw new RuntimeException("Not enough stock for snack: " + snack.getSnackName());
            }

            // Tạo BookingSnack
            BookingSnack bookingSnack = new BookingSnack();
            bookingSnack.setBooking(firstBooking);
            bookingSnack.setSnack(snack);
            bookingSnack.setQuantity(item.getQuantity());
            bookingSnack.setPriceAtPurchase(snack.getPrice());

            bookingSnackRepository.save(bookingSnack);

            // Trừ tồn kho
            if (snack.getStock() != null) {
                snack.setStock(snack.getStock() - item.getQuantity());
                snackRepository.save(snack);
            }

            totalSnacksCost += bookingSnack.getSubtotal();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("txnRef", txnRef);
        response.put("totalSnacksCost", totalSnacksCost);
        response.put("message", "Snacks added successfully");

        return response;
    }

    /**
     * Lấy danh sách snacks của một booking
     */
    public List<BookingSnackDTO> getBookingSnacks(Long bookingId) {
        return bookingSnackRepository.findByBooking_BookingId(bookingId).stream()
                .map(this::convertToBookingSnackDTO)
                .collect(Collectors.toList());
    }

    /**
     * Lấy danh sách snacks của một transaction
     */
    public List<BookingSnackDTO> getSnacksByTxnRef(String txnRef) {
        return bookingSnackRepository.findByTxnRef(txnRef).stream()
                .map(this::convertToBookingSnackDTO)
                .collect(Collectors.toList());
    }

    /**
     * Tính tổng tiền snacks của một transaction
     */
    public Double calculateSnacksTotalByTxnRef(String txnRef) {
        return bookingSnackRepository.findByTxnRef(txnRef).stream()
                .mapToDouble(BookingSnack::getSubtotal)
                .sum();
    }

    // Helper methods
    private SnackDTO convertToDTO(Snack snack) {
        return new SnackDTO(
                snack.getSnackId(),
                snack.getSnackName(),
                snack.getDescription(),
                snack.getPrice(),
                snack.getImageUrl(),
                snack.getStock(),
                snack.getCategory().name(),
                snack.getAvailable()
        );
    }

    private BookingSnackDTO convertToBookingSnackDTO(BookingSnack bs) {
        return new BookingSnackDTO(
                bs.getId(),
                bs.getSnack().getSnackId(),
                bs.getSnack().getSnackName(),
                bs.getQuantity(),
                bs.getPriceAtPurchase(),
                bs.getSubtotal()
        );
    }
}
