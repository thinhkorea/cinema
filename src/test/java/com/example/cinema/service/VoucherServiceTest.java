package com.example.cinema.service;

import com.example.cinema.domain.User;
import com.example.cinema.domain.Voucher;
import com.example.cinema.dto.VoucherAvailableDTO;
import com.example.cinema.dto.VoucherStatusDTO;
import com.example.cinema.dto.VoucherValidateResponseDTO;
import com.example.cinema.repository.BookingRepository;
import com.example.cinema.repository.SnackOrderRepository;
import com.example.cinema.repository.UserRepository;
import com.example.cinema.repository.VoucherClaimRepository;
import com.example.cinema.repository.VoucherRedemptionRepository;
import com.example.cinema.repository.VoucherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock private VoucherRepository voucherRepo;
    @Mock private VoucherRedemptionRepository redemptionRepo;
    @Mock private UserRepository userRepo;
    @Mock private BookingRepository bookingRepo;
    @Mock private SnackOrderRepository snackOrderRepo;
    @Mock private VoucherClaimRepository claimRepo;

    @Test
    void validateRejectsVoucherWhenCustomerHasNotReachedRequiredTotalSpent() {
        VoucherService service = service();
        Voucher voucher = voucher("VIP100", 1_000_000.0);
        User user = user();

        when(voucherRepo.findByCodeIgnoreCase("VIP100")).thenReturn(Optional.of(voucher));
        when(userRepo.findByEmailOrPhone(user.getEmail(), user.getEmail())).thenReturn(user);
        when(redemptionRepo.countByVoucher_VoucherIdAndUser_UserId(anyLong(), anyLong())).thenReturn(0);
        when(bookingRepo.sumPaidAmountByCustomerUserIdSince(anyLong(), any(LocalDateTime.class))).thenReturn(500_000.0);

        assertThatThrownBy(() -> service.validate("VIP100", user.getEmail(), 200_000.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getAvailableHidesVoucherWhenCustomerHasNotReachedRequiredTotalSpent() {
        VoucherService service = service();
        Voucher basicVoucher = voucher("BASIC", null);
        Voucher milestoneVoucher = voucher("VIP100", 1_000_000.0);
        User user = user();

        when(voucherRepo.findAll()).thenReturn(List.of(basicVoucher, milestoneVoucher));
        when(userRepo.findByEmailOrPhone(user.getEmail(), user.getEmail())).thenReturn(user);
        when(redemptionRepo.countByVoucher_VoucherIdAndUser_UserId(anyLong(), anyLong())).thenReturn(0);
        when(bookingRepo.sumPaidAmountByCustomerUserIdSince(anyLong(), any(LocalDateTime.class))).thenReturn(500_000.0);

        List<VoucherAvailableDTO> available = service.getAvailable(user.getEmail(), 200_000.0);

        assertThat(available)
                .extracting(VoucherAvailableDTO::getCode)
                .containsExactly("BASIC");
    }

    @Test
    void validateAcceptsVoucherWhenCustomerHasReachedRequiredTotalSpent() {
        VoucherService service = service();
        Voucher voucher = voucher("VIP100", 1_000_000.0);
        User user = user();

        when(voucherRepo.findByCodeIgnoreCase("VIP100")).thenReturn(Optional.of(voucher));
        when(userRepo.findByEmailOrPhone(user.getEmail(), user.getEmail())).thenReturn(user);
        when(redemptionRepo.countByVoucher_VoucherIdAndUser_UserId(anyLong(), anyLong())).thenReturn(0);
        when(bookingRepo.sumPaidAmountByCustomerUserIdSince(anyLong(), any(LocalDateTime.class))).thenReturn(1_500_000.0);

        VoucherValidateResponseDTO response = service.validate("VIP100", user.getEmail(), 200_000.0);

        assertThat(response.isValid()).isTrue();
        assertThat(response.getCode()).isEqualTo("VIP100");
        assertThat(response.getRequiredTotalSpent()).isEqualTo(1_000_000.0);
        assertThat(response.getSpendingWindowDays()).isEqualTo(365);
    }

    @Test
    void validateAcceptsReachedMilestoneVoucherWithoutClaim() {
        VoucherService service = service();
        Voucher voucher = voucher("VIP100", 1_000_000.0);
        User user = user();

        when(voucherRepo.findByCodeIgnoreCase("VIP100")).thenReturn(Optional.of(voucher));
        when(userRepo.findByEmailOrPhone(user.getEmail(), user.getEmail())).thenReturn(user);
        when(redemptionRepo.countByVoucher_VoucherIdAndUser_UserId(anyLong(), anyLong())).thenReturn(0);
        when(bookingRepo.sumPaidAmountByCustomerUserIdSince(anyLong(), any(LocalDateTime.class))).thenReturn(1_500_000.0);

        VoucherValidateResponseDTO response = service.validate("VIP100", user.getEmail(), 200_000.0);

        assertThat(response.isValid()).isTrue();
        assertThat(response.getCode()).isEqualTo("VIP100");
    }

    @Test
    void claimRejectsLowerMilestoneWhenHigherMilestoneIsAvailable() {
        VoucherService service = service();
        Voucher lowerVoucher = voucher("VIP100", 1_000_000.0);
        Voucher higherVoucher = voucher("VIP500", 5_000_000.0);
        User user = user();

        when(voucherRepo.findByCodeIgnoreCase("VIP100")).thenReturn(Optional.of(lowerVoucher));
        when(voucherRepo.findAll()).thenReturn(List.of(lowerVoucher, higherVoucher));
        when(userRepo.findByEmailOrPhone(user.getEmail(), user.getEmail())).thenReturn(user);
        when(redemptionRepo.countByVoucher_VoucherIdAndUser_UserId(anyLong(), anyLong())).thenReturn(0);
        when(bookingRepo.sumPaidAmountByCustomerUserIdSince(anyLong(), any(LocalDateTime.class))).thenReturn(6_000_000.0);
        when(claimRepo.existsByVoucher_VoucherIdAndUser_UserId(anyLong(), anyLong())).thenReturn(false);

        assertThatThrownBy(() -> service.claim("VIP100", user.getEmail()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getMyVouchersHidesNewMemberVoucherAfterPaidPurchase() {
        VoucherService service = service();
        Voucher newbieVoucher = voucher("NEWBIE1", null);
        newbieVoucher.setNewMemberOnly(true);
        Voucher regularVoucher = voucher("SAVE10", null);
        User user = user();

        when(voucherRepo.findAll()).thenReturn(List.of(newbieVoucher, regularVoucher));
        when(userRepo.findByEmailOrPhone(user.getEmail(), user.getEmail())).thenReturn(user);
        when(bookingRepo.existsByCustomer_User_UserIdAndStatus(
                user.getUserId(),
                com.example.cinema.domain.Booking.Status.PAID)).thenReturn(true);
        when(redemptionRepo.countByVoucher_VoucherIdAndUser_UserId(anyLong(), anyLong())).thenReturn(0);

        List<VoucherStatusDTO> vouchers = service.getMyVouchers(user.getEmail());

        assertThat(vouchers)
                .extracting(VoucherStatusDTO::getCode)
                .containsExactly("SAVE10");
    }

    private VoucherService service() {
        return new VoucherService(voucherRepo, redemptionRepo, userRepo, bookingRepo, snackOrderRepo, claimRepo);
    }

    private Voucher voucher(String code, Double requiredTotalSpent) {
        Voucher voucher = new Voucher();
        voucher.setVoucherId((long) Math.abs(code.hashCode()));
        voucher.setCode(code);
        voucher.setName(code);
        voucher.setType(Voucher.VoucherType.FIXED);
        voucher.setValue(10_000.0);
        voucher.setActive(true);
        voucher.setUsedCount(0);
        voucher.setPerUserLimit(1);
        voucher.setNewMemberOnly(false);
        voucher.setRequiredTotalSpent(requiredTotalSpent);
        return voucher;
    }

    private User user() {
        User user = new User();
        user.setUserId(1L);
        user.setEmail("customer@example.com");
        user.setRole(User.Role.CUSTOMER);
        user.setIsActive(true);
        return user;
    }

}



