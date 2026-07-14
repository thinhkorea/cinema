package com.example.cinema.service;

import com.example.cinema.domain.User;
import com.example.cinema.domain.Voucher;
import com.example.cinema.domain.VoucherClaim;
import com.example.cinema.domain.VoucherRedemption;
import com.example.cinema.domain.Booking;
import com.example.cinema.dto.VoucherRedeemRequestDTO;
import com.example.cinema.dto.VoucherAvailableDTO;
import com.example.cinema.dto.VoucherRequestDTO;
import com.example.cinema.dto.VoucherResponseDTO;
import com.example.cinema.dto.VoucherStatusDTO;
import com.example.cinema.dto.VoucherValidateResponseDTO;
import com.example.cinema.repository.BookingRepository;
import com.example.cinema.repository.SnackOrderRepository;
import com.example.cinema.repository.UserRepository;
import com.example.cinema.repository.VoucherClaimRepository;
import com.example.cinema.repository.VoucherRedemptionRepository;
import com.example.cinema.repository.VoucherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Service
public class VoucherService {

    private static final int DEFAULT_SPENDING_WINDOW_DAYS = 365;

    private final VoucherRepository voucherRepo;
    private final VoucherRedemptionRepository redemptionRepo;
    private final UserRepository userRepo;
    private final BookingRepository bookingRepo;
    private final SnackOrderRepository snackOrderRepo;
    private final VoucherClaimRepository claimRepo;

    public VoucherService(
            VoucherRepository voucherRepo,
            VoucherRedemptionRepository redemptionRepo,
            UserRepository userRepo,
            BookingRepository bookingRepo,
            SnackOrderRepository snackOrderRepo,
            VoucherClaimRepository claimRepo) {
        this.voucherRepo = voucherRepo;
        this.redemptionRepo = redemptionRepo;
        this.userRepo = userRepo;
        this.bookingRepo = bookingRepo;
        this.snackOrderRepo = snackOrderRepo;
        this.claimRepo = claimRepo;
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
                .requiredTotalSpent(voucher.getRequiredTotalSpent())
                .spendingWindowDays(getSpendingWindowDays(voucher))
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
        double highestAvailableSpendingThreshold = getHighestAvailableSpendingThreshold(vouchers, user, totalAmount);

        for (Voucher voucher : vouchers) {
            if (!isApplicable(voucher, user, totalAmount)) {
                continue;
            }
            if (isClaimRequired(voucher)
                    && voucher.getRequiredTotalSpent() < highestAvailableSpendingThreshold) {
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
                    .requiredTotalSpent(voucher.getRequiredTotalSpent())
                    .spendingWindowDays(getSpendingWindowDays(voucher))
                    .discountAmount(discount)
                    .finalAmount(Math.max(0, totalAmount - discount))
                    .newMemberOnly(voucher.getNewMemberOnly())
                    .build());
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<VoucherStatusDTO> getMyVouchers(String username) {
        User user = getUser(username);
        List<VoucherStatusDTO> result = new ArrayList<>();
        boolean hasAnyPaidPurchase = hasAnyPaidPurchase(user);

        for (Voucher voucher : voucherRepo.findAll()) {
            if (!isVisibleInCustomerList(voucher)) {
                continue;
            }
            if (Boolean.TRUE.equals(voucher.getNewMemberOnly()) && hasAnyPaidPurchase) {
                continue;
            }

            result.add(toStatus(voucher, user));
        }

        return result;
    }

    @Transactional
    public VoucherStatusDTO claim(String code, String username) {
        Voucher voucher = voucherRepo.findByCodeIgnoreCase(normalizeCode(code))
                .orElseThrow(() -> new IllegalArgumentException("Voucher không tồn tại"));
        User user = getUser(username);

        if (!isVisibleInCustomerList(voucher)) {
            throw new IllegalArgumentException("Voucher không khả dụng");
        }
        if (!isClaimRequired(voucher)) {
            throw new IllegalArgumentException("Voucher này không cần nhận thủ công");
        }

        double currentSpent = getTotalSpent(user, voucher);
        EligibilityStatus status = getCustomerEligibilityStatus(voucher, user, currentSpent);
        if (!status.eligible()) {
            throw new IllegalArgumentException(status.reason());
        }

        boolean alreadyClaimed = claimRepo.existsByVoucher_VoucherIdAndUser_UserId(
                voucher.getVoucherId(),
                user.getUserId());
        if (!alreadyClaimed && voucher.getRequiredTotalSpent() < getHighestClaimableSpendingThreshold(user)) {
            throw new IllegalArgumentException("Bạn đã đạt mốc cao hơn, vui lòng nhận voucher ở mốc cao nhất");
        }

        if (!alreadyClaimed) {
            VoucherClaim claim = new VoucherClaim();
            claim.setVoucher(voucher);
            claim.setUser(user);
            claimRepo.save(claim);
        }

        return toStatus(voucher, user);
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
                .requiredTotalSpent(voucher.getRequiredTotalSpent())
                .spendingWindowDays(getSpendingWindowDays(voucher))
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
            if (hasAnyPaidPurchase(user)) {
                throw new IllegalArgumentException("Voucher chỉ áp dụng cho lần mua đầu tiên");
            }
        }

        if (totalAmount == null || totalAmount <= 0) {
            throw new IllegalArgumentException("Tổng thanh toán không hợp lệ");
        }

        Double requiredTotalSpent = voucher.getRequiredTotalSpent();
        if (requiredTotalSpent != null && requiredTotalSpent > 0
                && getTotalSpent(user, voucher) < requiredTotalSpent) {
            throw new IllegalArgumentException(
                    "Khách hàng chưa đạt mốc chi tiêu trong " + formatSpendingWindow(voucher) + " để áp dụng voucher");
        }

        if (isClaimRequired(voucher)
                && voucher.getRequiredTotalSpent() < getHighestAvailableSpendingThreshold(voucherRepo.findAll(), user, totalAmount)) {
            throw new IllegalArgumentException("Khách hàng đã đạt mốc voucher cao hơn");
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
            if (hasAnyPaidPurchase(user)) {
                return false;
            }
        }

        if (totalAmount == null || totalAmount <= 0) {
            return false;
        }

        Double requiredTotalSpent = voucher.getRequiredTotalSpent();
        if (requiredTotalSpent != null && requiredTotalSpent > 0
                && getTotalSpent(user, voucher) < requiredTotalSpent) {
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

    private double getTotalSpent(User user) {
        Double bookingSpent = bookingRepo.sumPaidAmountByCustomerUserId(user.getUserId());
        Double snackSpent = snackOrderRepo.sumPaidStandaloneAmountByCustomerUserId(user.getUserId());
        return defaultAmount(bookingSpent) + defaultAmount(snackSpent);
    }

    private double getTotalSpent(User user, Voucher voucher) {
        Integer windowDays = getSpendingWindowDays(voucher);
        if (windowDays == null) {
            return getTotalSpent(user);
        }

        LocalDateTime from = getSpendingWindowStart(windowDays);
        Double bookingSpent = bookingRepo.sumPaidAmountByCustomerUserIdSince(user.getUserId(), from);
        Double snackSpent = snackOrderRepo.sumPaidStandaloneAmountByCustomerUserIdSince(
                user.getUserId(),
                from);
        return defaultAmount(bookingSpent) + defaultAmount(snackSpent);
    }

    private double defaultAmount(Double value) {
        return value == null ? 0.0 : value;
    }

    private VoucherStatusDTO toStatus(Voucher voucher, User user) {
        Integer windowDays = getSpendingWindowDays(voucher);
        double currentSpent = voucher.getRequiredTotalSpent() != null && voucher.getRequiredTotalSpent() > 0
                ? getTotalSpent(user, voucher)
                : 0.0;
        double remaining = voucher.getRequiredTotalSpent() == null
                ? 0.0
                : Math.max(0, voucher.getRequiredTotalSpent() - currentSpent);
        EligibilityStatus status = getCustomerEligibilityStatus(voucher, user, currentSpent);
        boolean claimed = status.eligible();

        return VoucherStatusDTO.builder()
                .code(voucher.getCode())
                .name(voucher.getName())
                .description(voucher.getDescription())
                .type(voucher.getType())
                .value(voucher.getValue())
                .maxDiscount(voucher.getMaxDiscount())
                .minOrder(voucher.getMinOrder())
                .requiredTotalSpent(voucher.getRequiredTotalSpent())
                .spendingWindowDays(windowDays)
                .currentTotalSpent(currentSpent)
                .remainingAmount(remaining)
                .eligible(status.eligible())
                .claimed(claimed)
                .claimable(false)
                .reason(status.reason())
                .newMemberOnly(voucher.getNewMemberOnly())
                .startAt(voucher.getStartAt())
                .endAt(voucher.getEndAt())
                .build();
    }

    private boolean isClaimRequired(Voucher voucher) {
        return voucher.getRequiredTotalSpent() != null && voucher.getRequiredTotalSpent() > 0;
    }

    private boolean hasClaimed(Voucher voucher, User user) {
        return claimRepo.existsByVoucher_VoucherIdAndUser_UserId(voucher.getVoucherId(), user.getUserId());
    }

    private double getHighestClaimableSpendingThreshold(User user) {
        double highest = 0.0;
        boolean hasAnyPaidPurchase = hasAnyPaidPurchase(user);

        for (Voucher candidate : voucherRepo.findAll()) {
            if (!isVisibleInCustomerList(candidate) || !isClaimRequired(candidate)) {
                continue;
            }
            if (Boolean.TRUE.equals(candidate.getNewMemberOnly()) && hasAnyPaidPurchase) {
                continue;
            }
            if (hasClaimed(candidate, user)) {
                continue;
            }

            double currentSpent = getTotalSpent(user, candidate);
            EligibilityStatus status = getCustomerEligibilityStatus(candidate, user, currentSpent);
            if (status.eligible()) {
                highest = Math.max(highest, candidate.getRequiredTotalSpent());
            }
        }

        return highest;
    }

    private double getHighestAvailableSpendingThreshold(List<Voucher> vouchers, User user, Double totalAmount) {
        double highest = 0.0;

        for (Voucher candidate : vouchers) {
            if (!isClaimRequired(candidate) || !isApplicable(candidate, user, totalAmount)) {
                continue;
            }

            highest = Math.max(highest, candidate.getRequiredTotalSpent());
        }

        return highest;
    }

    private boolean hasAnyPaidPurchase(User user) {
        boolean hasPaidBooking = bookingRepo.existsByCustomer_User_UserIdAndStatus(
                user.getUserId(),
                com.example.cinema.domain.Booking.Status.PAID);
        if (hasPaidBooking) {
            return true;
        }

        return defaultAmount(snackOrderRepo.sumPaidStandaloneAmountByCustomerUserId(user.getUserId())) > 0;
    }

    private Integer getSpendingWindowDays(Voucher voucher) {
        if (voucher.getRequiredTotalSpent() == null || voucher.getRequiredTotalSpent() <= 0) {
            return null;
        }

        Integer windowDays = voucher.getSpendingWindowDays();
        return windowDays == null || windowDays <= 0 ? DEFAULT_SPENDING_WINDOW_DAYS : windowDays;
    }

    private String formatSpendingWindow(Voucher voucher) {
        Integer windowDays = getSpendingWindowDays(voucher);
        return formatCalendarWindow(windowDays == null ? DEFAULT_SPENDING_WINDOW_DAYS : windowDays);
    }

    private int normalizeSpendingWindowDays(Integer windowDays) {
        int rawDays = windowDays == null || windowDays <= 0 ? DEFAULT_SPENDING_WINDOW_DAYS : windowDays;
        int years = Math.max(1, Math.round(rawDays / 365.0f));
        return years * DEFAULT_SPENDING_WINDOW_DAYS;
    }

    private LocalDateTime getSpendingWindowStart(int windowDays) {
        LocalDate today = LocalDate.now();
        int years = Math.max(1, Math.round(windowDays / 365.0f));
        int startYear = today.getYear() - years + 1;
        return LocalDate.of(startYear, 1, 1).atStartOfDay();
    }

    private String formatCalendarWindow(int windowDays) {
        LocalDate today = LocalDate.now();
        int years = Math.max(1, Math.round(windowDays / 365.0f));
        int startYear = today.getYear() - years + 1;
        if (years == 1) {
            return "năm " + today.getYear();
        }
        return "từ năm " + startYear + " đến năm " + today.getYear();
    }

    private boolean isVisibleInCustomerList(Voucher voucher) {
        if (voucher.getActive() == null || !voucher.getActive()) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        if (voucher.getEndAt() != null && now.isAfter(voucher.getEndAt())) {
            return false;
        }

        int usedCount = voucher.getUsedCount() == null ? 0 : voucher.getUsedCount();
        return voucher.getUsageLimit() == null || voucher.getUsageLimit() <= 0 || usedCount < voucher.getUsageLimit();
    }

    private EligibilityStatus getCustomerEligibilityStatus(Voucher voucher, User user, double currentSpent) {
        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStartAt() != null && now.isBefore(voucher.getStartAt())) {
            return new EligibilityStatus(false, "Chưa đến ngày áp dụng");
        }

        int perUserLimit = voucher.getPerUserLimit() == null ? 1 : voucher.getPerUserLimit();
        if (perUserLimit > 0) {
            int usedByUser = redemptionRepo.countByVoucher_VoucherIdAndUser_UserId(voucher.getVoucherId(), user.getUserId());
            if (usedByUser >= perUserLimit) {
                return new EligibilityStatus(false, "Đã hết lượt dùng cho tài khoản này");
            }
        }

        if (Boolean.TRUE.equals(voucher.getNewMemberOnly())) {
            if (hasAnyPaidPurchase(user)) {
                return new EligibilityStatus(false, "Chỉ áp dụng cho lần mua đầu tiên");
            }
        }

        Double requiredTotalSpent = voucher.getRequiredTotalSpent();
        if (requiredTotalSpent != null && requiredTotalSpent > 0 && currentSpent < requiredTotalSpent) {
            return new EligibilityStatus(false, "Cần đạt mốc chi tiêu trong " + formatSpendingWindow(voucher));
        }

        return new EligibilityStatus(true, "Có thể dùng khi đơn hàng đạt điều kiện");
    }

    private record EligibilityStatus(boolean eligible, String reason) {
    }

    private String normalizeCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Khach hang da dat moc voucher cao hon");
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
        Double requiredTotalSpent = request.getRequiredTotalSpent();
        voucher.setRequiredTotalSpent(
                requiredTotalSpent == null || requiredTotalSpent <= 0 ? null : requiredTotalSpent);
        Integer spendingWindowDays = request.getSpendingWindowDays();
        voucher.setSpendingWindowDays(
                requiredTotalSpent == null || requiredTotalSpent <= 0
                        ? null
                        : normalizeSpendingWindowDays(spendingWindowDays));
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
                .requiredTotalSpent(voucher.getRequiredTotalSpent())
                .spendingWindowDays(getSpendingWindowDays(voucher))
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


