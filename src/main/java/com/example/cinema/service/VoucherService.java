package com.example.cinema.service;

import com.example.cinema.domain.User;
import com.example.cinema.domain.Voucher;
import com.example.cinema.domain.VoucherRedemption;
import com.example.cinema.domain.Booking;
import com.example.cinema.dto.VoucherRedeemRequestDTO;
import com.example.cinema.dto.VoucherAvailableDTO;
import com.example.cinema.dto.VoucherRequestDTO;
import com.example.cinema.dto.VoucherResponseDTO;
import com.example.cinema.dto.VoucherValidateResponseDTO;
import com.example.cinema.repository.BookingRepository;
import com.example.cinema.repository.UserRepository;
import com.example.cinema.repository.VoucherRedemptionRepository;
import com.example.cinema.repository.VoucherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Service
public class VoucherService {

    private final VoucherRepository voucherRepo;
    private final VoucherRedemptionRepository redemptionRepo;
    private final UserRepository userRepo;
    private final BookingRepository bookingRepo;

    public VoucherService(
            VoucherRepository voucherRepo,
            VoucherRedemptionRepository redemptionRepo,
            UserRepository userRepo,
            BookingRepository bookingRepo) {
        this.voucherRepo = voucherRepo;
        this.redemptionRepo = redemptionRepo;
        this.userRepo = userRepo;
        this.bookingRepo = bookingRepo;
    }

    @Transactional(readOnly = true)
    public List<VoucherResponseDTO> getAll() {
        return voucherRepo.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public VoucherResponseDTO create(VoucherRequestDTO request) {
        Voucher voucher = new Voucher();
        applyRequest(voucher, request, true);
        return toResponse(voucherRepo.save(voucher));
    }

    @Transactional
    public VoucherResponseDTO update(Long voucherId, VoucherRequestDTO request) {
        Voucher voucher = voucherRepo.findById(voucherId)
                .orElseThrow(() -> new IllegalArgumentException("Voucher không tồn tại"));
        applyRequest(voucher, request, false);
        return toResponse(voucherRepo.save(voucher));
    }

    @Transactional
    public void delete(Long voucherId) {
        if (!voucherRepo.existsById(voucherId)) {
            throw new IllegalArgumentException("Voucher không tồn tại");
        }
        voucherRepo.deleteById(voucherId);
    }

    @Transactional
    public VoucherResponseDTO toggleActive(Long voucherId, boolean active) {
        Voucher voucher = voucherRepo.findById(voucherId)
                .orElseThrow(() -> new IllegalArgumentException("Voucher không tồn tại"));
        voucher.setActive(active);
        return toResponse(voucherRepo.save(voucher));
    }

    @Transactional(readOnly = true)
    public VoucherValidateResponseDTO validate(String code, String username, Double totalAmount) {
        Voucher voucher = voucherRepo.findByCodeIgnoreCase(normalizeCode(code))
                .orElseThrow(() -> new IllegalArgumentException("Voucher không tồn tại"));

        User user = getUser(username);
        validateVoucher(voucher, user, totalAmount);

        double discount = calculateDiscount(voucher, totalAmount);
        double finalAmount = Math.max(0, totalAmount - discount);

        return VoucherValidateResponseDTO.builder()
                .valid(true)
                .message("OK")
                .code(voucher.getCode())
                .type(voucher.getType())
                .value(voucher.getValue())
                .discountAmount(discount)
                .finalAmount(finalAmount)
                .maxDiscount(voucher.getMaxDiscount())
                .minOrder(voucher.getMinOrder())
                .build();
    }

    @Transactional(readOnly = true)
    public List<VoucherAvailableDTO> getAvailable(String username, Double totalAmount) {
        if (totalAmount == null || totalAmount <= 0) {
            throw new IllegalArgumentException("Tổng thanh toán không hợp lệ");
        }

        User user = getUser(username);
        List<Voucher> vouchers = voucherRepo.findAll();
        List<VoucherAvailableDTO> result = new ArrayList<>();

        for (Voucher voucher : vouchers) {
            if (!isApplicable(voucher, user, totalAmount)) {
                continue;
            }

            double discount = calculateDiscount(voucher, totalAmount);
            result.add(VoucherAvailableDTO.builder()
                    .code(voucher.getCode())
                    .name(voucher.getName())
                    .description(voucher.getDescription())
                    .type(voucher.getType())
                    .value(voucher.getValue())
                    .maxDiscount(voucher.getMaxDiscount())
                    .minOrder(voucher.getMinOrder())
                    .discountAmount(discount)
                    .finalAmount(Math.max(0, totalAmount - discount))
                    .newMemberOnly(voucher.getNewMemberOnly())
                    .build());
        }

        return result;
    }

    @Transactional
    public VoucherValidateResponseDTO redeem(VoucherRedeemRequestDTO request, String username) {
        Voucher voucher = voucherRepo.findByCodeIgnoreCase(normalizeCode(request.getCode()))
                .orElseThrow(() -> new IllegalArgumentException("Voucher không tồn tại"));

        User user = getUser(username);
        if (redemptionRepo.existsByVoucher_VoucherIdAndTxnRef(voucher.getVoucherId(), request.getTxnRef())) {
            throw new IllegalArgumentException("Voucher đã được dùng cho giao dịch này");
        }

        validateVoucher(voucher, user, request.getTotalAmount());

        double discount = calculateDiscount(voucher, request.getTotalAmount());
        VoucherRedemption redemption = new VoucherRedemption();
        redemption.setVoucher(voucher);
        redemption.setUser(user);
        redemption.setTxnRef(request.getTxnRef());
        redemption.setDiscountAmount(discount);
        redemption.setTotalAmount(request.getTotalAmount());
        redemptionRepo.save(redemption);

        applyVoucherToBookings(request.getTxnRef(), voucher, discount);

        voucher.setUsedCount((voucher.getUsedCount() == null ? 0 : voucher.getUsedCount()) + 1);
        voucherRepo.save(voucher);

        double finalAmount = Math.max(0, request.getTotalAmount() - discount);
        return VoucherValidateResponseDTO.builder()
                .valid(true)
                .message("OK")
                .code(voucher.getCode())
                .type(voucher.getType())
                .value(voucher.getValue())
                .discountAmount(discount)
                .finalAmount(finalAmount)
                .maxDiscount(voucher.getMaxDiscount())
                .minOrder(voucher.getMinOrder())
                .build();
    }

    private void applyVoucherToBookings(String txnRef, Voucher voucher, double discount) {
        List<Booking> bookings = bookingRepo.findByTxnRef(txnRef);
        if (bookings.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy booking với mã: " + txnRef);
        }

        Booking first = bookings.get(0);
        if (first.getVoucherCode() != null && first.getVoucherDiscount() != null
                && first.getVoucherDiscount() > 0) {
            return;
        }

        List<Booking> updated = new ArrayList<>(bookings.size());
        for (int i = 0; i < bookings.size(); i++) {
            Booking booking = bookings.get(i);
            double originalTotal = booking.getTotal() == null ? 0.0 : booking.getTotal();
            if (i == 0) {
                booking.setVoucherCode(voucher.getCode());
                booking.setVoucherDiscount(discount);
                booking.setTotal(Math.max(0, originalTotal - discount));
            } else {
                booking.setVoucherCode(null);
                booking.setVoucherDiscount(0.0);
                booking.setTotal(originalTotal);
            }
            updated.add(booking);
        }

        bookingRepo.saveAll(updated);
    }

    private void validateVoucher(Voucher voucher, User user, Double totalAmount) {
        if (voucher.getActive() == null || !voucher.getActive()) {
            throw new IllegalArgumentException("Voucher đang tạm khóa");
        }

        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStartAt() != null && now.isBefore(voucher.getStartAt())) {
            throw new IllegalArgumentException("Voucher chưa đến ngày áp dụng");
        }
        if (voucher.getEndAt() != null && now.isAfter(voucher.getEndAt())) {
            throw new IllegalArgumentException("Voucher đã hết hạn");
        }

        int usedCount = voucher.getUsedCount() == null ? 0 : voucher.getUsedCount();
        if (voucher.getUsageLimit() != null && voucher.getUsageLimit() > 0 && usedCount >= voucher.getUsageLimit()) {
            throw new IllegalArgumentException("Voucher đã hết lượt sử dụng");
        }

        int perUserLimit = voucher.getPerUserLimit() == null ? 1 : voucher.getPerUserLimit();
        if (perUserLimit > 0) {
            int usedByUser = redemptionRepo.countByVoucher_VoucherIdAndUser_UserId(voucher.getVoucherId(), user.getUserId());
            if (usedByUser >= perUserLimit) {
                throw new IllegalArgumentException("Voucher đã vượt quá số lần sử dụng cho tài khoản này");
            }
        }

        if (Boolean.TRUE.equals(voucher.getNewMemberOnly())) {
            boolean hasPaidBooking = bookingRepo.existsByCustomer_User_UserIdAndStatus(
                    user.getUserId(),
                    com.example.cinema.domain.Booking.Status.PAID);
            if (hasPaidBooking) {
                throw new IllegalArgumentException("Voucher chỉ áp dụng cho lần mua đầu tiên");
            }
        }

        if (totalAmount == null || totalAmount <= 0) {
            throw new IllegalArgumentException("Tổng thanh toán không hợp lệ");
        }

        if (voucher.getMinOrder() != null && totalAmount < voucher.getMinOrder()) {
            throw new IllegalArgumentException("Đơn hàng chưa đạt tối thiểu để áp dụng voucher");
        }
    }

    private boolean isApplicable(Voucher voucher, User user, Double totalAmount) {
        if (voucher.getActive() == null || !voucher.getActive()) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStartAt() != null && now.isBefore(voucher.getStartAt())) {
            return false;
        }
        if (voucher.getEndAt() != null && now.isAfter(voucher.getEndAt())) {
            return false;
        }

        int usedCount = voucher.getUsedCount() == null ? 0 : voucher.getUsedCount();
        if (voucher.getUsageLimit() != null && voucher.getUsageLimit() > 0 && usedCount >= voucher.getUsageLimit()) {
            return false;
        }

        int perUserLimit = voucher.getPerUserLimit() == null ? 1 : voucher.getPerUserLimit();
        if (perUserLimit > 0) {
            int usedByUser = redemptionRepo.countByVoucher_VoucherIdAndUser_UserId(voucher.getVoucherId(), user.getUserId());
            if (usedByUser >= perUserLimit) {
                return false;
            }
        }

        if (Boolean.TRUE.equals(voucher.getNewMemberOnly())) {
            boolean hasPaidBooking = bookingRepo.existsByCustomer_User_UserIdAndStatus(
                    user.getUserId(),
                    com.example.cinema.domain.Booking.Status.PAID);
            if (hasPaidBooking) {
                return false;
            }
        }

        if (totalAmount == null || totalAmount <= 0) {
            return false;
        }

        if (voucher.getMinOrder() != null && totalAmount < voucher.getMinOrder()) {
            return false;
        }

        return true;
    }

    private double calculateDiscount(Voucher voucher, Double totalAmount) {
        double discount;
        if (voucher.getType() == Voucher.VoucherType.FIXED) {
            discount = voucher.getValue() == null ? 0.0 : voucher.getValue();
        } else {
            double percent = voucher.getValue() == null ? 0.0 : voucher.getValue();
            discount = totalAmount * (percent / 100.0);
        }

        if (voucher.getMaxDiscount() != null) {
            discount = Math.min(discount, voucher.getMaxDiscount());
        }

        return Math.min(discount, totalAmount);
    }

    private User getUser(String username) {
        User user = userRepo.findByEmailOrPhone(username, username);
        if (user == null) {
            throw new IllegalArgumentException("Không tìm thấy người dùng");
        }
        return user;
    }

    private String normalizeCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập mã voucher");
        }
        return code.trim();
    }

    private void applyRequest(Voucher voucher, VoucherRequestDTO request, boolean isCreate) {
        if (request.getCode() == null || request.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Mã voucher không được để trống");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên voucher không được để trống");
        }

        String code = request.getCode().trim();
        if (isCreate) {
            if (voucherRepo.findByCodeIgnoreCase(code).isPresent()) {
                throw new IllegalArgumentException("Mã voucher đã tồn tại");
            }
        } else if (!code.equalsIgnoreCase(voucher.getCode())) {
            if (voucherRepo.findByCodeIgnoreCase(code).isPresent()) {
                throw new IllegalArgumentException("Mã voucher đã tồn tại");
            }
        }

        voucher.setCode(code);
        voucher.setName(request.getName().trim());
        voucher.setDescription(request.getDescription());
        voucher.setType(request.getType() == null ? Voucher.VoucherType.PERCENT : request.getType());
        voucher.setValue(request.getValue() == null ? 0.0 : request.getValue());
        voucher.setMaxDiscount(request.getMaxDiscount());
        voucher.setMinOrder(request.getMinOrder());
        voucher.setActive(request.getActive() == null ? Boolean.TRUE : request.getActive());
        voucher.setStartAt(request.getStartAt());
        voucher.setEndAt(request.getEndAt());
        voucher.setUsageLimit(request.getUsageLimit());
        voucher.setPerUserLimit(request.getPerUserLimit() == null ? 1 : request.getPerUserLimit());
        voucher.setNewMemberOnly(request.getNewMemberOnly() != null && request.getNewMemberOnly());
    }

    private VoucherResponseDTO toResponse(Voucher voucher) {
        return VoucherResponseDTO.builder()
                .voucherId(voucher.getVoucherId())
                .code(voucher.getCode())
                .name(voucher.getName())
                .description(voucher.getDescription())
                .type(voucher.getType())
                .value(voucher.getValue())
                .maxDiscount(voucher.getMaxDiscount())
                .minOrder(voucher.getMinOrder())
                .active(voucher.getActive())
                .startAt(voucher.getStartAt())
                .endAt(voucher.getEndAt())
                .usageLimit(voucher.getUsageLimit())
                .usedCount(voucher.getUsedCount())
                .perUserLimit(voucher.getPerUserLimit())
                .newMemberOnly(voucher.getNewMemberOnly())
                .build();
    }
}
