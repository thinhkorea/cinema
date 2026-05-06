package com.example.cinema.service;

import com.example.cinema.domain.Booking;
import com.example.cinema.domain.Customer;
import com.example.cinema.domain.PointTransaction;
import com.example.cinema.dto.PointTransactionDTO;
import com.example.cinema.repository.CustomerRepository;
import com.example.cinema.repository.PointTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PointService {

    private static final int POINT_EXPIRY_MONTHS = 3;

    private final PointTransactionRepository pointTransactionRepo;
    private final CustomerRepository customerRepo;

    public PointService(PointTransactionRepository pointTransactionRepo, CustomerRepository customerRepo) {
        this.pointTransactionRepo = pointTransactionRepo;
        this.customerRepo = customerRepo;
    }

    /**
     * Lấy điểm khả dụng của khách hàng (chưa hết hạn).
     * Nếu khách hàng chưa có giao dịch điểm nào (hệ thống cũ), sẽ tự migrate.
     */
    @Transactional
    public int getAvailablePoints(Long customerId) {
        Customer customer = customerRepo.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Khách hàng không tồn tại"));
        migrateIfNeeded(customer);
        Integer available = pointTransactionRepo.sumAvailablePoints(customerId, LocalDateTime.now());
        return available != null ? available : 0;
    }

    /**
     * Tích điểm khi mua vé thành công.
     * Tỉ lệ: 20.000đ = 1 điểm. Điểm này không có hạn sử dụng.
     */
    @Transactional
    public int earnPoints(Customer customer, Booking booking, double paidAmount) {
        migrateIfNeeded(customer);
        int points = (int) Math.floor(paidAmount / 20000.0);
        if (points <= 0) return 0;

        PointTransaction tx = new PointTransaction();
        tx.setCustomer(customer);
        tx.setBooking(booking);
        tx.setType(PointTransaction.Type.EARNED);
        tx.setPoints(points);
        tx.setDescription("Tích điểm mua vé #" + booking.getBookingId());
        tx.setCreatedAt(LocalDateTime.now());
        tx.setExpiredAt(null);
        pointTransactionRepo.save(tx);

        int newPoints = (customer.getLoyaltyPoints() != null ? customer.getLoyaltyPoints() : 0) + points;
        customer.setLoyaltyPoints(newPoints);
        customerRepo.save(customer);

        return points;
    }

    /**
     * Hoàn điểm khi hủy vé.
     * Tỉ lệ: 1.000đ = 1 điểm. Điểm hoàn có hạn sử dụng 6 tháng.
     */
    @Transactional
    public int refundPoints(Customer customer, Booking booking, double refundAmount) {
        migrateIfNeeded(customer);
        int points = (int) Math.floor(refundAmount / 1000.0);
        if (points <= 0) return 0;

        LocalDateTime expiredAt = LocalDateTime.now().plusMonths(POINT_EXPIRY_MONTHS);

        PointTransaction tx = new PointTransaction();
        tx.setCustomer(customer);
        tx.setBooking(booking);
        tx.setType(PointTransaction.Type.REFUND_CANCEL);
        tx.setPoints(points);
        tx.setDescription("Hoàn điểm hủy vé #" + booking.getBookingId()
                + " (hạn dùng: " + expiredAt.toLocalDate() + ")");
        tx.setCreatedAt(LocalDateTime.now());
        tx.setExpiredAt(expiredAt);
        pointTransactionRepo.save(tx);

        int newPoints = (customer.getLoyaltyPoints() != null ? customer.getLoyaltyPoints() : 0) + points;
        customer.setLoyaltyPoints(newPoints);
        customerRepo.save(customer);

        return points;
    }

    /**
     * Trừ điểm khi dùng để giảm giá.
     * Điểm được trừ theo thứ tự: điểm sắp hết hạn trước.
     */
    @Transactional
    public void usePoints(Customer customer, int pointsToUse) {
        migrateIfNeeded(customer);
        int available = getAvailablePoints(customer.getCustomerId());
        if (pointsToUse > available) {
            throw new IllegalArgumentException("Điểm không đủ. Hiện có: " + available + " điểm khả dụng.");
        }

        PointTransaction tx = new PointTransaction();
        tx.setCustomer(customer);
        tx.setBooking(null);
        tx.setType(PointTransaction.Type.USED);
        tx.setPoints(-pointsToUse);
        tx.setDescription("Dùng " + pointsToUse + " điểm để giảm " + (pointsToUse * 1000) + "đ");
        tx.setCreatedAt(LocalDateTime.now());
        tx.setExpiredAt(null);
        pointTransactionRepo.save(tx);

        int newPoints = (customer.getLoyaltyPoints() != null ? customer.getLoyaltyPoints() : 0) - pointsToUse;
        customer.setLoyaltyPoints(Math.max(0, newPoints));
        customerRepo.save(customer);
    }

    /**
     * Lấy lịch sử điểm của khách hàng.
     */
    @Transactional(readOnly = true)
    public List<PointTransactionDTO> getHistory(Long customerId) {
        return pointTransactionRepo.findByCustomer_CustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(PointTransactionDTO::from)
                .collect(Collectors.toList());
    }

    /**
     * Migrate điểm cũ (loyaltyPoints) sang hệ thống PointTransaction nếu chưa có bản ghi nào.
     */
    @Transactional
    public void migrateIfNeeded(Customer customer) {
        if (customer.getLoyaltyPoints() != null
                && customer.getLoyaltyPoints() > 0
                && !pointTransactionRepo.existsByCustomer_CustomerId(customer.getCustomerId())) {

            PointTransaction legacy = new PointTransaction();
            legacy.setCustomer(customer);
            legacy.setBooking(null);
            legacy.setType(PointTransaction.Type.EARNED);
            legacy.setPoints(customer.getLoyaltyPoints());
            legacy.setDescription("Điểm tích lũy cũ (migrate)");
            legacy.setCreatedAt(LocalDateTime.now());
            legacy.setExpiredAt(null);
            pointTransactionRepo.save(legacy);
        }
    }
}
