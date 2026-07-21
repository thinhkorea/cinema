package com.example.cinema.service;

import com.example.cinema.domain.Booking;
import com.example.cinema.domain.Customer;
import com.example.cinema.domain.Snack;
import com.example.cinema.domain.SnackOrder;
import com.example.cinema.domain.SnackOrderItem;
import com.example.cinema.domain.User;
import com.example.cinema.dto.BookingSnackDTO;
import com.example.cinema.dto.CreateSnackOrderRequestDTO;
import com.example.cinema.dto.SnackItemRequestDTO;
import com.example.cinema.dto.SnackOrderItemResponseDTO;
import com.example.cinema.dto.SnackOrderResponseDTO;
import com.example.cinema.repository.BookingRepository;
import com.example.cinema.repository.CustomerRepository;
import com.example.cinema.repository.SnackOrderItemRepository;
import com.example.cinema.repository.SnackOrderRepository;
import com.example.cinema.repository.SnackRepository;
import com.example.cinema.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SnackOrderService {

    private static final int STANDALONE_PENDING_TIMEOUT_MINUTES = 30;

    private final SnackOrderRepository snackOrderRepository;
    private final SnackOrderItemRepository snackOrderItemRepository;
    private final SnackRepository snackRepository;
    private final CustomerRepository customerRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    @Transactional
    public SnackOrderResponseDTO createOrder(String username, CreateSnackOrderRequestDTO request) {
        Customer customer = resolveCustomerByPrincipal(username);

        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Vui long chon it nhat mot mon bap nuoc.");
        }

        SnackOrder order = SnackOrder.builder()
                .orderCode(generateOrderCode())
                .customer(customer)
                .orderType(SnackOrder.OrderType.STANDALONE)
                .status(SnackOrder.Status.PENDING)
                .paymentMethod(null)
                .note(request.getNote())
                .totalAmount(0.0)
                .build();

        return saveOrderItems(order, request.getItems());
    }

    @Transactional
    public SnackOrderResponseDTO createOrReplaceBookingOrder(String txnRef, List<SnackItemRequestDTO> itemRequests) {
        if (txnRef == null || txnRef.isBlank()) {
            throw new IllegalArgumentException("Ma giao dich khong hop le.");
        }

        List<Booking> bookings = bookingRepository.findByTxnRef(txnRef);
        if (bookings.isEmpty()) {
            throw new IllegalArgumentException("Khong tim thay ve voi ma giao dich: " + txnRef);
        }

        Booking representativeBooking = bookings.get(0);
        Customer customer = representativeBooking.getCustomer();

        SnackOrder order = snackOrderRepository.findFirstByBookingTxnRefOrderByCreatedAtDesc(txnRef)
                .orElseGet(() -> SnackOrder.builder()
                        .orderCode(generateOrderCode())
                        .build());

        order.setCustomer(customer);
        order.setBooking(representativeBooking);
        order.setBookingTxnRef(txnRef);
        order.setOrderType(SnackOrder.OrderType.BOOKING_ATTACHED);
        order.setStatus(resolveBookingSnackStatus(bookings));
        order.setPaymentMethod(resolveBookingPaymentMethod(bookings));
        order.setNote(null);
        if (order.getStatus() == SnackOrder.Status.PAID && order.getPaidAt() == null) {
            order.setPaidAt(LocalDateTime.now());
        }
        if (order.getStatus() != SnackOrder.Status.PAID) {
            order.setPaidAt(null);
        }

        return saveOrderItems(order, itemRequests);
    }

    @Transactional(readOnly = true)
    public List<SnackOrderResponseDTO> getMyOrders(String username) {
        Customer customer = resolveCustomerByPrincipal(username);

        return snackOrderRepository.findByCustomer_CustomerIdOrderByCreatedAtDesc(customer.getCustomerId())
                .stream()
                .map(order -> toResponse(order, loadItems(order)))
                .toList();
    }

    @Transactional(readOnly = true)
    public SnackOrderResponseDTO getOrderByCode(String orderCode, String username) {
        SnackOrder order = snackOrderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay don bap nuoc."));

        if (username != null && !username.isBlank()) {
            String ownerEmail = order.getCustomer() != null && order.getCustomer().getUser() != null
                    ? order.getCustomer().getUser().getEmail()
                    : null;
            if (ownerEmail != null && !ownerEmail.equalsIgnoreCase(username)) {
                throw new IllegalArgumentException("Ban khong co quyen xem don bap nuoc nay.");
            }
        }

        return toResponse(order, loadItems(order));
    }

    @Transactional
    public SnackOrderResponseDTO markPaidByOrderCode(String orderCode, String paymentMethod) {
        SnackOrder order = snackOrderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay don bap nuoc."));

        if (isExpiredStandalonePendingOrder(order)) {
            throw new IllegalArgumentException("Don bap nuoc da qua han thanh toan. Vui long tao don moi.");
        }
        if (order.getStatus() == SnackOrder.Status.CANCELLED) {
            throw new IllegalArgumentException("Don bap nuoc da bi huy. Vui long tao don moi.");
        }
        if (order.getStatus() == SnackOrder.Status.PAID) {
            return toResponse(order, loadItems(order));
        }

        order.setStatus(SnackOrder.Status.PAID);
        order.setPaymentMethod(normalizePaymentMethod(paymentMethod));
        order.setPaidAt(LocalDateTime.now());
        snackOrderRepository.save(order);

        return toResponse(order, loadItems(order));
    }

    @Transactional
    public void markPaidByBookingTxnRef(String txnRef, String paymentMethod) {
        snackOrderRepository.findFirstByBookingTxnRefOrderByCreatedAtDesc(txnRef)
                .ifPresent(order -> {
                    order.setStatus(SnackOrder.Status.PAID);
                    order.setPaymentMethod(normalizePaymentMethod(paymentMethod));
                    if (order.getPaidAt() == null) {
                        order.setPaidAt(LocalDateTime.now());
                    }
                    snackOrderRepository.save(order);
                });
    }

    @Transactional(readOnly = true)
    public List<SnackOrderItem> getBookingOrderItems(String txnRef) {
        return snackOrderRepository.findFirstByBookingTxnRefOrderByCreatedAtDesc(txnRef)
                .map(this::loadItems)
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public List<BookingSnackDTO> getBookingSnackDTOs(String txnRef) {
        return getBookingOrderItems(txnRef).stream()
                .map(this::toBookingSnackDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Double getBookingSnackTotal(String txnRef) {
        return getBookingOrderItems(txnRef).stream()
                .mapToDouble(SnackOrderItem::getSubtotal)
                .sum();
    }

    private SnackOrderResponseDTO saveOrderItems(SnackOrder order, List<SnackItemRequestDTO> itemRequests) {
        SnackOrder managedOrder = snackOrderRepository.save(order);
        if (managedOrder.getItems() == null) {
            managedOrder.setItems(new ArrayList<>());
        }
        List<SnackOrderItem> managedItems = managedOrder.getItems();
        managedItems.clear();

        double totalAmount = 0.0;

        if (itemRequests != null) {
            for (SnackItemRequestDTO itemRequest : itemRequests) {
                if (itemRequest == null || itemRequest.getSnackId() == null || itemRequest.getQuantity() == null) {
                    continue;
                }
                if (itemRequest.getQuantity() <= 0) {
                    continue;
                }

                Snack snack = snackRepository.findById(itemRequest.getSnackId())
                        .orElseThrow(() -> new IllegalArgumentException("Khong tim thay snack voi ID: " + itemRequest.getSnackId()));

                if (!Boolean.TRUE.equals(snack.getAvailable())) {
                    throw new IllegalArgumentException("Snack " + snack.getSnackName() + " hien khong con ban.");
                }

                SnackOrderItem orderItem = SnackOrderItem.builder()
                        .snackOrder(managedOrder)
                        .snack(snack)
                        .quantity(itemRequest.getQuantity())
                        .priceAtPurchase(snack.getPrice())
                        .build();
                totalAmount += orderItem.getSubtotal();
                managedItems.add(orderItem);
            }
        }

        if (managedItems.isEmpty() && managedOrder.getOrderType() == SnackOrder.OrderType.STANDALONE) {
            throw new IllegalArgumentException("Vui long chon it nhat mot mon bap nuoc hop le.");
        }

        managedOrder.setTotalAmount(totalAmount);
        snackOrderRepository.save(managedOrder);

        return toResponse(managedOrder, new ArrayList<>(managedItems));
    }

    private List<SnackOrderItem> loadItems(SnackOrder order) {
        return snackOrderItemRepository.findBySnackOrder_SnackOrderIdOrderBySnackOrderItemIdAsc(order.getSnackOrderId());
    }

    private String generateOrderCode() {
        return "SNK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private Customer resolveCustomerByPrincipal(String principal) {
        if (principal == null || principal.isBlank()) {
            throw new IllegalArgumentException("Ban can dang nhap bang tai khoan khach hang de dat bap nuoc.");
        }

        return customerRepository.findByUser_Email(principal)
                .or(() -> {
                    User user = userRepository.findByEmailOrPhone(principal, principal);
                    return user == null ? java.util.Optional.empty() : customerRepository.findByUserUserId(user.getUserId());
                })
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tai khoan chua co ho so khach hang. Vui long cap nhat ho so truoc khi dat bap nuoc."));
    }

    private String normalizePaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return "VNPAY";
        }
        return paymentMethod.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isExpiredStandalonePendingOrder(SnackOrder order) {
        return order.getOrderType() == SnackOrder.OrderType.STANDALONE
                && order.getStatus() == SnackOrder.Status.PENDING
                && order.getCreatedAt() != null
                && order.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(STANDALONE_PENDING_TIMEOUT_MINUTES));
    }

    private SnackOrder.Status resolveBookingSnackStatus(List<Booking> bookings) {
        boolean paid = bookings.stream().allMatch(booking -> booking.getStatus() == Booking.Status.PAID);
        return paid ? SnackOrder.Status.PAID : SnackOrder.Status.PENDING;
    }

    private String resolveBookingPaymentMethod(List<Booking> bookings) {
        return bookings.stream()
                .map(Booking::getPaymentMethod)
                .filter(method -> method != null && !method.isBlank())
                .findFirst()
                .orElse(null);
    }

    private SnackOrderResponseDTO toResponse(SnackOrder order, List<SnackOrderItem> items) {
        return SnackOrderResponseDTO.builder()
                .snackOrderId(order.getSnackOrderId())
                .orderCode(order.getOrderCode())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .paymentMethod(order.getPaymentMethod())
                .note(order.getNote())
                .createdAt(order.getCreatedAt())
                .paidAt(order.getPaidAt())
                .items(items.stream().map(item -> SnackOrderItemResponseDTO.builder()
                        .snackId(item.getSnack().getSnackId())
                        .snackName(item.getSnack().getSnackName())
                        .imageUrl(item.getSnack().getImageUrl())
                        .quantity(item.getQuantity())
                        .priceAtPurchase(item.getPriceAtPurchase())
                        .subtotal(item.getSubtotal())
                        .build()).toList())
                .build();
    }

    private BookingSnackDTO toBookingSnackDTO(SnackOrderItem item) {
        return BookingSnackDTO.builder()
                .id(item.getSnackOrderItemId())
                .snackId(item.getSnack().getSnackId())
                .snackName(item.getSnack().getSnackName())
                .quantity(item.getQuantity())
                .priceAtPurchase(item.getPriceAtPurchase())
                .subtotal(item.getSubtotal())
                .build();
    }
}
