package com.example.cinema.service;

import com.example.cinema.domain.*;
import com.example.cinema.dto.BookingResponseDTO;
import com.example.cinema.dto.ShiftRevenueItemDTO;
import com.example.cinema.dto.SoldTicketDTO;
import com.example.cinema.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final Pattern SEAT_PATTERN = Pattern.compile("^([A-Za-z]+)(\\d+)");

    private final BookingRepository bookingRepo;
    private final ShowtimeRepository showtimeRepo;
    private final SeatRepository seatRepo;
    private final StaffRepository staffRepo;
    private final CustomerRepository customerRepo;
    private final UserRepository userRepository;
    private final TicketEmailService ticketEmailService;
    private final PointService pointService;
    private final BookingSnackRepository bookingSnackRepo;

    public BookingService(BookingRepository bookingRepo,
            ShowtimeRepository showtimeRepo,
            SeatRepository seatRepo,
            StaffRepository staffRepo, 
            CustomerRepository customerRepo,
            UserRepository userRepository,
            TicketEmailService ticketEmailService,
            PointService pointService,
            BookingSnackRepository bookingSnackRepo) {
        this.bookingRepo = bookingRepo;
        this.showtimeRepo = showtimeRepo;
        this.seatRepo = seatRepo;
        this.staffRepo = staffRepo;
        this.customerRepo = customerRepo;
        this.userRepository = userRepository;
        this.ticketEmailService = ticketEmailService;
        this.pointService = pointService;
        this.bookingSnackRepo = bookingSnackRepo;
    }

    // ==================== ADMIN / USER ====================
        public List<BookingResponseDTO> findAllDTO() {
        return bookingRepo.findAll().stream()
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

    public List<Booking> findByUsername(String username) {
        return bookingRepo.findByCustomer_User_Email(username);
    }

    public List<Booking> findByShowtimeId(Long showtimeId) {
        return bookingRepo.findByShowtime_ShowtimeId(showtimeId);
    }

    public List<Booking> findByStatus(Booking.Status status) {
        return bookingRepo.findByStatus(status);
    }

    public Optional<Booking> findById(Long id) {
        return bookingRepo.findById(id);
    }

    public void saveAll(List<Booking> bookings) {
        bookingRepo.saveAll(bookings);
    }

    public List<Booking> findByStaffUsernameAndStatus(String username, Booking.Status status) {
        return bookingRepo.findBySoldByStaff_User_EmailAndStatus(username, status);
    }

    @Transactional(readOnly = true)
    public List<Booking> getBookingsByUsername(String username) {
        User user = userRepository.findByEmailOrPhone(username, username);
        if (user == null) {
            throw new RuntimeException("User not found: " + username);
        }
        return bookingRepo.findByCustomer_User_Email(user.getEmail());
    }

    // Phương thức mới để lấy Booking theo ID, xử lý logic tìm kiếm trong Service
    public Booking getBookingById(Long bookingId) {
        return bookingRepo.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy vé với ID: " + bookingId));
    }

    @Transactional(readOnly = true) // Thêm @Transactional để đảm bảo các lazy-loading hoạt động
    public List<SoldTicketDTO> findSoldTicketsByStaffUsername(String username) {
        List<Booking> bookings = bookingRepo.findBySoldByStaff_User_EmailAndStatus(username, Booking.Status.PAID);
        return bookings.stream()
            .map(SoldTicketDTO::fromBooking)
            .sorted(Comparator.comparing(SoldTicketDTO::getCreatedAt).reversed())
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SoldTicketDTO> findSoldTicketsByStaffUsernameAndDate(String username, LocalDateTime dateStart, LocalDateTime dateEnd) {
        List<Booking> bookings = bookingRepo.findBySoldByStaff_User_EmailAndStatusAndDateRange(username, Booking.Status.PAID, dateStart, dateEnd);
        return bookings.stream()
            .map(SoldTicketDTO::fromBooking)
            .sorted(Comparator.comparing(SoldTicketDTO::getCreatedAt).reversed())
            .collect(Collectors.toList());
    }

    // Thêm phương thức mới để tìm booking theo txnRef
    public List<Booking> findByTxnRef(String txnRef) {
        return bookingRepo.findByTxnRef(txnRef);
    }

    // ==================== STAFF / VNPay ====================
    @Transactional
    public List<Booking> createMultiBooking(Long showtimeId, List<Long> seatIds, String txnRef,
            String staffUsername, Integer total, String paymentMethod) {

        Showtime showtime = showtimeRepo.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Showtime not found"));

        // Tìm nhân viên bán vé dựa trên username
        Staff staff = staffRepo.findByUser_Email(staffUsername)
                .orElseThrow(() -> new IllegalArgumentException("Staff not found with username: " + staffUsername));

        List<Seat> selectedSeats = seatIds.stream()
                .map(seatId -> seatRepo.findById(seatId)
                        .orElseThrow(() -> new IllegalArgumentException("Seat not found: " + seatId)))
                .collect(Collectors.toList());

        for (Seat seat : selectedSeats) {
            if (!seat.getRoom().getRoomId().equals(showtime.getRoom().getRoomId())) {
                throw new IllegalStateException("Seat not in showtime room");
            }
        }

        validateSeatSelectionRules(showtimeId, showtime, selectedSeats);

        List<Booking> list = new ArrayList<>();

        for (Seat seat : selectedSeats) {

            if (bookingRepo.existsByShowtime_ShowtimeIdAndSeat_SeatId(showtimeId, seat.getSeatId()))
                throw new IllegalStateException("Seat already booked: " + seat.getSeatNumber());

            seat.setBooking(true);
            seatRepo.save(seat);

            Booking b = new Booking();
            b.setShowtime(showtime);
            b.setSeat(seat);
            b.setStatus(Booking.Status.PENDING);
            b.setTxnRef(txnRef);
            b.setSoldByStaff(staff);
            b.setPaymentMethod(paymentMethod != null ? paymentMethod : "CASH");
            // Gán giá vé
            if (total != null && total > 0) {
                b.setTotal((double) total / seatIds.size());
            } else {
                // Tính giá vé dựa trên loại ghế
                double basePrice = showtime.getPrice().doubleValue();
                double seatPrice = calculateSeatPrice(basePrice, seat.getSeatType());
                b.setTotal(seatPrice);
            }
            list.add(b);
        }

        return bookingRepo.saveAll(list);
    }

    @Transactional
    public List<Booking> markPaidByTxn(String txnRef, String paymentMethod) {
        List<Booking> bookings = bookingRepo.findByTxnRef(txnRef);
        if (bookings.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy booking nào với mã giao dịch: " + txnRef);
        }
        
        List<Booking> updatedBookings = new ArrayList<>();
        boolean hasNewlyPaidBooking = false;
        
        // Tính tổng tiền của toàn bộ giao dịch
        Double totalAmount = 0.0;
        Customer customer = null;
        
        for (Booking b : bookings) {
            if (b.getStatus() != Booking.Status.PAID) {
                hasNewlyPaidBooking = true;
            }
            b.setStatus(Booking.Status.PAID);
            if (b.getPaymentMethod() == null || b.getPaymentMethod().isBlank()) {
                b.setPaymentMethod(paymentMethod != null ? paymentMethod : "CASH");
            }
            
            if (b.getTotal() != null) {
                totalAmount += b.getTotal();
            }
            if (customer == null && b.getCustomer() != null) {
                customer = b.getCustomer();
            }
            
            updatedBookings.add(bookingRepo.save(b));
        }
        
        // TÍCH ĐIỂM: Tính từ tổng tiền THANH TOÁN (sau khi trừ điểm)
        if (customer != null && totalAmount > 0) {
            Integer pointsUsed = bookings.get(0).getPointsUsed() != null ?
                bookings.get(0).getPointsUsed() : 0;

            Double discountFromPoints = pointsUsed * 1000.0;
            Double actualPaymentAmount = totalAmount - discountFromPoints;

            // Trừ điểm đã dùng qua PointService
            if (pointsUsed > 0) {
                pointService.usePoints(customer, pointsUsed);
            }

            // Tích điểm mới từ tiền thanh toán thực tế
            if (actualPaymentAmount > 0) {
                Booking firstBooking = updatedBookings.get(0);
                pointService.earnPoints(customer, firstBooking, actualPaymentAmount);
            }
        }

        // Gửi email vé bất đồng bộ để tránh làm chậm response xác nhận thanh toán.
        if (hasNewlyPaidBooking && customer != null && customer.getUser() != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    ticketEmailService.sendPaidTicketEmail(updatedBookings);
                } catch (Exception ex) {
                    log.warn("Không thể gửi email vé cho txnRef={}: {}", txnRef, ex.getMessage());
                }
            });
        }
        
        return updatedBookings;
    }

    // ==================== THỐNG KÊ ====================
    public List<Map<String, Object>> getMonthlyRevenue() {
        List<Object[]> results = bookingRepo.getMonthlyRevenue();
        List<Map<String, Object>> list = new ArrayList<>();
        Map<Object, Double> snackRevenueByMonth = toRevenueMap(bookingSnackRepo.getMonthlySnackRevenue());
        for (Object[] r : results) {
            double ticketRevenue = toDouble(r[1]);
            double snackRevenue = snackRevenueByMonth.getOrDefault(r[0], 0.0);
            list.add(Map.of(
                    "month", r[0],
                    "revenue", ticketRevenue + snackRevenue,
                    "ticketRevenue", ticketRevenue,
                    "snackRevenue", snackRevenue));
            snackRevenueByMonth.remove(r[0]);
        }
        for (Map.Entry<Object, Double> entry : snackRevenueByMonth.entrySet()) {
            list.add(Map.of(
                    "month", entry.getKey(),
                    "revenue", entry.getValue(),
                    "ticketRevenue", 0.0,
                    "snackRevenue", entry.getValue()));
        }
        list.sort(Comparator.comparing(row -> String.valueOf(row.get("month"))));
        return list;
    }

    public List<Map<String, Object>> getMonthlyRevenueByYear(int year) {
        Map<Object, Double> snackRevenueByMonth = toRevenueMap(bookingSnackRepo.findMonthlySnackRevenueByYear(year));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] r : bookingRepo.findMonthlyRevenueByYear(year)) {
            double ticketRevenue = toDouble(r[1]);
            double snackRevenue = snackRevenueByMonth.getOrDefault(r[0], 0.0);
            list.add(Map.of(
                    "month", r[0],
                    "revenue", ticketRevenue + snackRevenue,
                    "ticketRevenue", ticketRevenue,
                    "snackRevenue", snackRevenue));
            snackRevenueByMonth.remove(r[0]);
        }
        for (Map.Entry<Object, Double> entry : snackRevenueByMonth.entrySet()) {
            list.add(Map.of(
                    "month", entry.getKey(),
                    "revenue", entry.getValue(),
                    "ticketRevenue", 0.0,
                    "snackRevenue", entry.getValue()));
        }
        list.sort(Comparator.comparingInt(row -> ((Number) row.get("month")).intValue()));
        return list;
    }

    public List<Map<String, Object>> getRevenueByMovie() {
        List<Object[]> results = bookingRepo.getRevenueByMovie();
        List<Map<String, Object>> movieRevenueList = new ArrayList<>();
        Map<Object, Double> snackRevenueByMovie = toRevenueMap(bookingSnackRepo.getSnackRevenueByMovie());
        for (Object[] row : results) {
            double ticketRevenue = toDouble(row[1]);
            double snackRevenue = snackRevenueByMovie.getOrDefault(row[0], 0.0);
            movieRevenueList.add(Map.of(
                    "movieTitle", row[0],
                    "revenue", ticketRevenue + snackRevenue,
                    "ticketRevenue", ticketRevenue,
                    "snackRevenue", snackRevenue));
            snackRevenueByMovie.remove(row[0]);
        }
        for (Map.Entry<Object, Double> entry : snackRevenueByMovie.entrySet()) {
            movieRevenueList.add(Map.of(
                    "movieTitle", entry.getKey(),
                    "revenue", entry.getValue(),
                    "ticketRevenue", 0.0,
                    "snackRevenue", entry.getValue()));
        }
        movieRevenueList.sort((left, right) -> Double.compare(toDouble(right.get("revenue")), toDouble(left.get("revenue"))));
        return movieRevenueList;
    }

    public List<Map<String, Object>> getRevenueByStaff() {
        List<Object[]> results = bookingRepo.getRevenueByStaff();
        List<Map<String, Object>> staffRevenueList = new ArrayList<>();
        Map<Object, Double> snackRevenueByStaff = toRevenueMap(bookingSnackRepo.getSnackRevenueByStaff());
        for (Object[] row : results) {
            double ticketRevenue = toDouble(row[1]);
            double snackRevenue = snackRevenueByStaff.getOrDefault(row[0], 0.0);
            staffRevenueList.add(Map.of(
                    "staffName", row[0],
                    "totalRevenue", ticketRevenue + snackRevenue,
                    "ticketRevenue", ticketRevenue,
                    "snackRevenue", snackRevenue));
            snackRevenueByStaff.remove(row[0]);
        }
        for (Map.Entry<Object, Double> entry : snackRevenueByStaff.entrySet()) {
            staffRevenueList.add(Map.of(
                    "staffName", entry.getKey(),
                    "totalRevenue", entry.getValue(),
                    "ticketRevenue", 0.0,
                    "snackRevenue", entry.getValue()));
        }
        staffRevenueList.sort((left, right) -> Double.compare(toDouble(right.get("totalRevenue")), toDouble(left.get("totalRevenue"))));
        return staffRevenueList;
    }

    public List<Map<String, Object>> getRevenueByTimeSlot(int year) {
        Map<String, TimeSlotRevenue> slots = new LinkedHashMap<>();
        slots.put("morning", new TimeSlotRevenue("morning", "Sáng", "08:30 - 11:59", 0));
        slots.put("afternoon", new TimeSlotRevenue("afternoon", "Chiều", "12:00 - 17:59", 1));
        slots.put("evening", new TimeSlotRevenue("evening", "Tối", "18:00 - 22:59", 2));
        slots.put("late", new TimeSlotRevenue("late", "Suất muộn", "23:00 - 23:59", 3));

        for (Object[] row : bookingRepo.getRevenueByShowtimeTime(year)) {
            TimeSlotRevenue slot = slots.get(getTimeSlotKey(toInt(row[0]), toInt(row[1])));
            if (slot != null) {
                slot.ticketRevenue += toDouble(row[2]);
            }
        }

        for (Object[] row : bookingSnackRepo.getSnackRevenueByShowtimeTime(year)) {
            TimeSlotRevenue slot = slots.get(getTimeSlotKey(toInt(row[0]), toInt(row[1])));
            if (slot != null) {
                slot.snackRevenue += toDouble(row[2]);
            }
        }

        return slots.values().stream()
                .sorted(Comparator.comparingInt(slot -> slot.order))
                .map(slot -> Map.<String, Object>of(
                        "slot", slot.key,
                        "label", slot.label,
                        "timeRange", slot.timeRange,
                        "revenue", slot.ticketRevenue + slot.snackRevenue,
                        "ticketRevenue", slot.ticketRevenue,
                        "snackRevenue", slot.snackRevenue))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ShiftRevenueItemDTO> getDailyShiftRevenue(LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        LocalDateTime from = targetDate.atStartOfDay();
        LocalDateTime to = targetDate.plusDays(1).atStartOfDay().minusNanos(1);
        Map<String, ShiftRevenueAccumulator> rows = new LinkedHashMap<>();

        for (Booking booking : bookingRepo.findByStatusAndCreatedAtBetween(Booking.Status.PAID, from, to)) {
            if (booking.getSoldByStaff() == null) {
                continue;
            }
            ShiftDefinition shift = getShiftDefinition(booking.getCreatedAt());
            if (shift == null) {
                continue;
            }
            ShiftRevenueAccumulator row = getShiftRevenueRow(rows, shift, getBookingStaffName(booking));
            double amount = booking.getTotal() != null ? booking.getTotal() : 0.0;
            if ("VNPAY".equals(normalizeRevenuePaymentMethod(booking.getPaymentMethod()))) {
                row.ticketVnpayRevenue += amount;
            } else {
                row.ticketCashRevenue += amount;
            }
        }

        for (Booking booking : bookingRepo.findByPopcornAdditionalCollectedAtBetween(from, to)) {
            double amount = booking.getPopcornAdditionalCharge() != null ? booking.getPopcornAdditionalCharge() : 0.0;
            if (amount <= 0 || booking.getPopcornAdditionalCollectedAt() == null) {
                continue;
            }
            ShiftDefinition shift = getShiftDefinition(booking.getPopcornAdditionalCollectedAt());
            if (shift == null) {
                continue;
            }
            String staffName = booking.getPopcornAdditionalCollectedBy();
            if (staffName == null || staffName.isBlank()) {
                staffName = getBookingStaffName(booking);
            }
            ShiftRevenueAccumulator row = getShiftRevenueRow(rows, shift, staffName);
            String method = normalizeRevenuePaymentMethod(booking.getPopcornAdditionalPaymentMethod());
            if ("VNPAY".equals(method)) {
                row.popcornVnpayRevenue += amount;
            } else if ("BANK".equals(method) || "TRANSFER".equals(method)) {
                row.popcornBankRevenue += amount;
            } else {
                row.popcornCashRevenue += amount;
            }
        }

        return rows.values().stream()
                .sorted(Comparator
                        .comparingInt((ShiftRevenueAccumulator row) -> row.shift.order)
                        .thenComparing(row -> row.staffName, String.CASE_INSENSITIVE_ORDER))
                .map(ShiftRevenueAccumulator::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ShiftRevenueItemDTO> getDailyShiftRevenueForStaff(LocalDate date, String username) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        LocalDateTime from = targetDate.atStartOfDay();
        LocalDateTime to = targetDate.plusDays(1).atStartOfDay().minusNanos(1);
        return getRevenueForStaffBetween(from, to, username);
    }

    @Transactional(readOnly = true)
    public List<ShiftRevenueItemDTO> getRevenueForStaffBetween(LocalDateTime from, LocalDateTime to, String username) {
        Map<String, ShiftRevenueAccumulator> rows = new LinkedHashMap<>();

        for (Booking booking : bookingRepo.findByStatusAndCreatedAtBetween(Booking.Status.PAID, from, to)) {
            if (!isBookingSoldByStaff(booking, username)) {
                continue;
            }
            ShiftDefinition shift = getShiftDefinition(booking.getCreatedAt());
            if (shift == null) {
                continue;
            }
            ShiftRevenueAccumulator row = getShiftRevenueRow(rows, shift, getBookingStaffName(booking));
            double amount = booking.getTotal() != null ? booking.getTotal() : 0.0;
            if ("VNPAY".equals(normalizeRevenuePaymentMethod(booking.getPaymentMethod()))) {
                row.ticketVnpayRevenue += amount;
            } else {
                row.ticketCashRevenue += amount;
            }
        }

        for (Booking booking : bookingRepo.findByPopcornAdditionalCollectedAtBetween(from, to)) {
            if (!isPopcornAdditionalCollectedByStaff(booking, username)) {
                continue;
            }
            double amount = booking.getPopcornAdditionalCharge() != null ? booking.getPopcornAdditionalCharge() : 0.0;
            if (amount <= 0 || booking.getPopcornAdditionalCollectedAt() == null) {
                continue;
            }
            ShiftDefinition shift = getShiftDefinition(booking.getPopcornAdditionalCollectedAt());
            if (shift == null) {
                continue;
            }
            String staffName = booking.getPopcornAdditionalCollectedBy();
            if (staffName == null || staffName.isBlank()) {
                staffName = getBookingStaffName(booking);
            }
            ShiftRevenueAccumulator row = getShiftRevenueRow(rows, shift, staffName);
            String method = normalizeRevenuePaymentMethod(booking.getPopcornAdditionalPaymentMethod());
            if ("VNPAY".equals(method)) {
                row.popcornVnpayRevenue += amount;
            } else if ("BANK".equals(method) || "TRANSFER".equals(method)) {
                row.popcornBankRevenue += amount;
            } else {
                row.popcornCashRevenue += amount;
            }
        }

        return rows.values().stream()
                .sorted(Comparator
                        .comparingInt((ShiftRevenueAccumulator row) -> row.shift.order)
                        .thenComparing(row -> row.staffName, String.CASE_INSENSITIVE_ORDER))
                .map(ShiftRevenueAccumulator::toDto)
                .collect(Collectors.toList());
    }

    private Map<Object, Double> toRevenueMap(List<Object[]> rows) {
        Map<Object, Double> revenueMap = new LinkedHashMap<>();
        for (Object[] row : rows) {
            revenueMap.put(row[0], toDouble(row[1]));
        }
        return revenueMap;
    }

    private double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String getTimeSlotKey(int hour, int minute) {
        int time = hour * 60 + minute;
        if (time >= 8 * 60 + 30 && time < 12 * 60) {
            return "morning";
        }
        if (time >= 12 * 60 && time < 18 * 60) {
            return "afternoon";
        }
        if (time >= 18 * 60 && time < 23 * 60) {
            return "evening";
        }
        if (time >= 23 * 60 && time < 24 * 60) {
            return "late";
        }
        return null;
    }

    private ShiftDefinition getShiftDefinition(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        LocalTime time = dateTime.toLocalTime();
        if (!time.isBefore(LocalTime.of(8, 30)) && time.isBefore(LocalTime.NOON)) {
            return new ShiftDefinition("morning", "Sáng", "08:30 - 11:59", 0);
        }
        if (!time.isBefore(LocalTime.NOON) && time.isBefore(LocalTime.of(18, 0))) {
            return new ShiftDefinition("afternoon", "Chiều", "12:00 - 17:59", 1);
        }
        if (!time.isBefore(LocalTime.of(18, 0)) && time.isBefore(LocalTime.of(23, 0))) {
            return new ShiftDefinition("evening", "Tối", "18:00 - 22:59", 2);
        }
        if (!time.isBefore(LocalTime.of(23, 0))) {
            return new ShiftDefinition("late", "Suất muộn", "23:00 - 23:59", 3);
        }
        return null;
    }

    private ShiftRevenueAccumulator getShiftRevenueRow(
            Map<String, ShiftRevenueAccumulator> rows,
            ShiftDefinition shift,
            String staffName) {
        String normalizedStaffName = staffName == null || staffName.isBlank() ? "Không xác định" : staffName.trim();
        String key = shift.key + "|" + normalizedStaffName.toLowerCase(Locale.ROOT);
        return rows.computeIfAbsent(key, ignored -> new ShiftRevenueAccumulator(shift, normalizedStaffName));
    }

    private String getBookingStaffName(Booking booking) {
        if (booking != null && booking.getSoldByStaff() != null && booking.getSoldByStaff().getUser() != null) {
            String fullName = booking.getSoldByStaff().getUser().getFullName();
            if (fullName != null && !fullName.isBlank()) {
                return fullName;
            }
            String email = booking.getSoldByStaff().getUser().getEmail();
            if (email != null && !email.isBlank()) {
                return email;
            }
        }
        return "Không xác định";
    }

    private boolean isBookingSoldByStaff(Booking booking, String username) {
        if (username == null || username.isBlank()
                || booking == null
                || booking.getSoldByStaff() == null
                || booking.getSoldByStaff().getUser() == null) {
            return false;
        }
        String email = booking.getSoldByStaff().getUser().getEmail();
        String phone = booking.getSoldByStaff().getUser().getPhone();
        return username.equalsIgnoreCase(email) || username.equalsIgnoreCase(phone);
    }

    private boolean isPopcornAdditionalCollectedByStaff(Booking booking, String username) {
        if (username == null || username.isBlank() || booking == null) {
            return false;
        }
        String collector = booking.getPopcornAdditionalCollectedBy();
        if (collector != null && username.equalsIgnoreCase(collector)) {
            return true;
        }
        return isBookingSoldByStaff(booking, username);
    }

    private String normalizeRevenuePaymentMethod(String paymentMethod) {
        return paymentMethod == null ? "CASH" : paymentMethod.trim().toUpperCase(Locale.ROOT);
    }

    private static class TimeSlotRevenue {
        private final String key;
        private final String label;
        private final String timeRange;
        private final int order;
        private double ticketRevenue;
        private double snackRevenue;

        private TimeSlotRevenue(String key, String label, String timeRange, int order) {
            this.key = key;
            this.label = label;
            this.timeRange = timeRange;
            this.order = order;
        }
    }

    private static class ShiftDefinition {
        private final String key;
        private final String label;
        private final String timeRange;
        private final int order;

        private ShiftDefinition(String key, String label, String timeRange, int order) {
            this.key = key;
            this.label = label;
            this.timeRange = timeRange;
            this.order = order;
        }
    }

    private static class ShiftRevenueAccumulator {
        private final ShiftDefinition shift;
        private final String staffName;
        private double ticketCashRevenue;
        private double ticketVnpayRevenue;
        private double popcornCashRevenue;
        private double popcornVnpayRevenue;
        private double popcornBankRevenue;

        private ShiftRevenueAccumulator(ShiftDefinition shift, String staffName) {
            this.shift = shift;
            this.staffName = staffName;
        }

        private ShiftRevenueItemDTO toDto() {
            double totalRevenue = ticketCashRevenue + ticketVnpayRevenue
                    + popcornCashRevenue + popcornVnpayRevenue + popcornBankRevenue;
            return ShiftRevenueItemDTO.builder()
                    .shiftKey(shift.key)
                    .shiftLabel(shift.label)
                    .timeRange(shift.timeRange)
                    .staffName(staffName)
                    .ticketCashRevenue(ticketCashRevenue)
                    .ticketVnpayRevenue(ticketVnpayRevenue)
                    .popcornCashRevenue(popcornCashRevenue)
                    .popcornVnpayRevenue(popcornVnpayRevenue)
                    .popcornBankRevenue(popcornBankRevenue)
                    .totalRevenue(totalRevenue)
                    .build();
        }
    }

    /** CUSTOMER */
    @Transactional
    public List<Booking> createMultiBookingForCustomer(Long showtimeId, List<Long> seatIds, String txnRef,
            String customerUsername) {
        Showtime showtime = showtimeRepo.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Showtime not found"));

        // Tìm khách hàng theo principal đăng nhập (email/phone) và fallback theo userId.
        Customer customer = resolveCustomerByPrincipal(customerUsername);

        // Lấy giá vé từ showtime (hoặc dùng mặc định nếu null)
        double basePrice = showtime.getPrice() != null ? showtime.getPrice() : 95000.0;

        List<Seat> selectedSeats = seatIds.stream()
                .map(seatId -> seatRepo.findById(seatId)
                        .orElseThrow(() -> new IllegalArgumentException("Seat not found: " + seatId)))
                .collect(Collectors.toList());

        for (Seat seat : selectedSeats) {
            if (!seat.getRoom().getRoomId().equals(showtime.getRoom().getRoomId())) {
                throw new IllegalStateException("Seat not in showtime room");
            }
        }

        validateSeatSelectionRules(showtimeId, showtime, selectedSeats);

        List<Booking> list = new ArrayList<>();

        for (Seat seat : selectedSeats) {

            if (bookingRepo.existsByShowtime_ShowtimeIdAndSeat_SeatId(showtimeId, seat.getSeatId()))
                throw new IllegalStateException("Seat already booked: " + seat.getSeatNumber());

            seat.setBooking(true);
            seatRepo.save(seat);

            // Tính giá vé dựa trên loại ghế
            double seatPrice = calculateSeatPrice(basePrice, seat.getSeatType());

            Booking b = new Booking();
            b.setShowtime(showtime);
            b.setSeat(seat);
            b.setCustomer(customer);
            b.setStatus(Booking.Status.PENDING);
            b.setTxnRef(txnRef);
            b.setPaymentMethod("VNPAY");
            b.setTotal(seatPrice);

            list.add(b);
        }

        return bookingRepo.saveAll(list);
    }

    private Customer resolveCustomerByPrincipal(String principal) {
        if (principal == null || principal.isBlank()) {
            throw new IllegalArgumentException("Không xác định được người dùng đăng nhập.");
        }

        Optional<Customer> customerByEmail = customerRepo.findByUser_Email(principal);
        if (customerByEmail.isPresent()) {
            return customerByEmail.get();
        }

        User user = userRepository.findByEmailOrPhone(principal, principal);
        if (user == null) {
            throw new IllegalArgumentException("Không tìm thấy user đăng nhập: " + principal);
        }

        return customerRepo.findByUserUserId(user.getUserId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tài khoản chưa có hồ sơ khách hàng. Vui lòng cập nhật hồ sơ trước khi đặt vé."));
    }

    // Tính giá vé dựa trên loại ghế
    private double calculateSeatPrice(double basePrice, Seat.SeatType seatType) {
        switch (seatType) {
            case VIP:
                return basePrice + 10000.0; // Phụ thu VIP: +10k
            case SWEETBOX:
                return basePrice * 2.0 + 15000.0; // Ghế đôi: x2 giá cơ bản + 15k
            case NORMAL:
            default:
                return basePrice; // Giá cơ bản
        }
    }

    private void validateSeatSelectionRules(Long showtimeId, Showtime showtime, List<Seat> selectedSeats) {
        if (selectedSeats == null || selectedSeats.isEmpty()) {
            throw new IllegalStateException("Bạn chưa chọn ghế.");
        }

        if (selectedSeats.size() > 5) {
            throw new IllegalStateException("Mỗi lần đặt chỉ tối đa 5 ghế. Vui lòng bỏ bớt ghế.");
        }

        Set<Long> uniqueSeatIds = selectedSeats.stream().map(Seat::getSeatId).collect(Collectors.toSet());
        if (uniqueSeatIds.size() != selectedSeats.size()) {
            throw new IllegalStateException("Danh sách ghế không hợp lệ (trùng ghế).");
        }

        Set<String> rowLabels = selectedSeats.stream()
                .map(this::extractSeatRow)
                .collect(Collectors.toSet());
        if (rowLabels.size() != 1) {
            throw new IllegalStateException("Bạn chỉ có thể chọn ghế trong cùng một hàng.");
        }

        List<Integer> selectedOrders = selectedSeats.stream()
            .flatMap(seat -> extractSeatOrders(seat).stream())
            .distinct()
                .sorted()
                .collect(Collectors.toList());

        for (int i = 1; i < selectedOrders.size(); i++) {
            if (selectedOrders.get(i) - selectedOrders.get(i - 1) != 1) {
                throw new IllegalStateException("Ghế cần liền kề nhau (ví dụ D1-D2 hoặc D2-D3).");
            }
        }

        String rowLabel = rowLabels.iterator().next();
        Set<Integer> selectedOrderSet = new HashSet<>(selectedOrders);

        Set<Integer> bookedOrdersInRow = seatRepo.findByRoom_RoomId(showtime.getRoom().getRoomId()).stream()
                .filter(seat -> rowLabel.equals(extractSeatRow(seat)))
                .filter(seat -> bookingRepo.existsByShowtime_ShowtimeIdAndSeat_SeatId(showtimeId, seat.getSeatId()))
            .flatMap(seat -> extractSeatOrders(seat).stream())
                .collect(Collectors.toSet());

        for (Integer bookedOrder : bookedOrdersInRow) {
            for (Integer selectedOrder : selectedOrderSet) {
                if (Math.abs(bookedOrder - selectedOrder) == 2) {
                    int middle = (bookedOrder + selectedOrder) / 2;
                    if (!bookedOrdersInRow.contains(middle) && !selectedOrderSet.contains(middle)) {
                        throw new IllegalStateException(
                                "Không thể để trống 1 ghế lẻ giữa ghế đã đặt và ghế bạn chọn.");
                    }
                }
            }
        }
    }

    private String extractSeatRow(Seat seat) {
        String seatNumber = seat != null && seat.getSeatNumber() != null ? seat.getSeatNumber().trim() : "";
        Matcher matcher = SEAT_PATTERN.matcher(seatNumber);
        if (!matcher.find()) {
            throw new IllegalStateException("Định dạng số ghế không hợp lệ: " + seatNumber);
        }
        return matcher.group(1).toUpperCase();
    }

    // private int extractSeatOrder(Seat seat) {
    //     String seatNumber = seat != null && seat.getSeatNumber() != null ? seat.getSeatNumber().trim() : "";
    //     Matcher matcher = SEAT_PATTERN.matcher(seatNumber);
    //     if (!matcher.find()) {
    //         throw new IllegalStateException("Định dạng số ghế không hợp lệ: " + seatNumber);
    //     }
    //     return Integer.parseInt(matcher.group(2));
    // }

    private List<Integer> extractSeatOrders(Seat seat) {
        String seatNumber = seat != null && seat.getSeatNumber() != null ? seat.getSeatNumber().trim() : "";

        Matcher singleSeatMatcher = SEAT_PATTERN.matcher(seatNumber);
        if (!seatNumber.contains("-")) {
            if (!singleSeatMatcher.find()) {
                throw new IllegalStateException("Định dạng số ghế không hợp lệ: " + seatNumber);
            }
            return List.of(Integer.parseInt(singleSeatMatcher.group(2)));
        }

        // Hỗ trợ ghế dải như F1-2, F11-12 để validation liền kề/ghế lẻ chính xác.
        Matcher rangeSeatMatcher = Pattern.compile("^[A-Za-z]+(\\d+)-(\\d+)$").matcher(seatNumber);
        if (!rangeSeatMatcher.find()) {
            throw new IllegalStateException("Định dạng số ghế không hợp lệ: " + seatNumber);
        }

        int start = Integer.parseInt(rangeSeatMatcher.group(1));
        int end = Integer.parseInt(rangeSeatMatcher.group(2));
        if (end < start) {
            int temp = start;
            start = end;
            end = temp;
        }

        List<Integer> orders = new ArrayList<>();
        for (int order = start; order <= end; order++) {
            orders.add(order);
        }
        return orders;
    }

    // Dùng điểm để giảm giá trước khi thanh toán
    @Transactional
    public Map<String, Object> redeemPoints(String txnRef, Integer pointsToUse) {
        List<Booking> bookings = bookingRepo.findByTxnRef(txnRef);
        if (bookings.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy booking với mã: " + txnRef);
        }

        Customer customer = bookings.get(0).getCustomer();
        if (customer == null) {
            throw new IllegalArgumentException("Booking này không có khách hàng");
        }

        int availablePoints = pointService.getAvailablePoints(customer.getCustomerId());
        if (pointsToUse < 0 || pointsToUse > availablePoints) {
            throw new IllegalArgumentException("Số điểm không hợp lệ. Bạn có: " + availablePoints + " điểm khả dụng");
        }

        Double totalAmount = bookings.stream()
                .mapToDouble(b -> b.getTotal() != null ? b.getTotal() : 0.0)
                .sum();

        Double discount = pointsToUse * 1000.0;

        if (discount > totalAmount) {
            throw new IllegalArgumentException("Số điểm quá lớn, tối đa có thể dùng: " + (int)(totalAmount / 1000) + " điểm");
        }

        for (int i = 0; i < bookings.size(); i++) {
            Booking b = bookings.get(i);
            Double originalPrice = b.getTotal() != null ? b.getTotal() : 0.0;
            if (i == 0) {
                b.setPointsUsed(pointsToUse);
                b.setTotal(originalPrice - discount);
            } else {
                b.setPointsUsed(0);
                b.setTotal(originalPrice);
            }
        }
        bookingRepo.saveAll(bookings);

        return Map.of(
                "message", "Lưu " + pointsToUse + " điểm thành công",
                "pointsUsed", pointsToUse,
                "discountAmount", discount,
                "totalAmount", totalAmount,
                "newTotal", totalAmount - discount,
                "remainingPoints", availablePoints
        );
    }

    // Thêm vào interface BookingService
    @Transactional
    public void cancelPendingBookingsByTxn(String txnRef) {
        List<Booking> bookingsToCancel = bookingRepo.findByTxnRefAndStatus(txnRef, Booking.Status.PENDING);
        if (!bookingsToCancel.isEmpty()) {
            bookingRepo.deleteAll(bookingsToCancel);
            System.out.println("Đã hủy " + bookingsToCancel.size() + " booking PENDING với txnRef: " + txnRef);
        }
    }

    @Transactional
    public Map<String, Object> cancelBookingByCustomer(Long bookingId, String username) {
        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vé với ID: " + bookingId));

        if (username == null || username.isBlank()) {
            throw new SecurityException("Bạn cần đăng nhập để hủy vé.");
        }

        Customer customer = booking.getCustomer();
        if (customer == null || customer.getUser() == null) {
            throw new IllegalStateException("Vé này không có thông tin khách hàng.");
        }

        String ownerEmail = customer.getUser().getEmail();
        String ownerPhone = customer.getUser().getPhone();
        boolean isOwner = (ownerEmail != null && ownerEmail.equalsIgnoreCase(username))
                || (ownerPhone != null && ownerPhone.equalsIgnoreCase(username));
        if (!isOwner) {
            throw new SecurityException("Bạn không có quyền hủy vé này.");
        }

        if (booking.getStatus() != Booking.Status.PAID) {
            throw new IllegalStateException("Chỉ có thể hủy vé đã thanh toán. Trạng thái hiện tại: "
                    + booking.getStatus());
        }

        if (booking.getShowtime() == null || booking.getShowtime().getStartTime() == null) {
            throw new IllegalStateException("Vé này không có thông tin giờ chiếu.");
        }

        LocalDateTime showtimeStart = booking.getShowtime().getStartTime();
        LocalDateTime deadline = showtimeStart.minusMinutes(30);
        if (LocalDateTime.now().isAfter(deadline)) {
            throw new IllegalStateException(
                    "Không thể hủy vé. Chỉ được hủy trước giờ chiếu ít nhất 30 phút. " +
                    "Giờ chiếu: " + showtimeStart);
        }

        booking.setStatus(Booking.Status.CANCELLED);
        Seat seat = booking.getSeat();
        if (seat != null) {
            seat.setBooking(false);
            seatRepo.save(seat);
        }
        bookingRepo.save(booking);

        double refundAmount = booking.getTotal() != null ? booking.getTotal() : 0.0;
        int refundedPoints = pointService.refundPoints(customer, booking, refundAmount);

        int availablePoints = pointService.getAvailablePoints(customer.getCustomerId());

        return Map.of(
                "message", "Hủy vé thành công! Bạn được hoàn " + refundedPoints + " điểm (hạn dùng 3 tháng).",
                "bookingId", bookingId,
                "refundedPoints", refundedPoints,
                "refundAmount", refundAmount,
                "totalAvailablePoints", availablePoints
        );
    }

    @Transactional
    public Map<String, Object> cancelBookingGroupByCustomer(String txnRef, String username) {
        if (txnRef == null || txnRef.isBlank()) {
            throw new IllegalArgumentException("Mã giao dịch không hợp lệ.");
        }
        if (username == null || username.isBlank()) {
            throw new SecurityException("Bạn cần đăng nhập để hủy vé.");
        }

        List<Booking> bookings = bookingRepo.findByTxnRef(txnRef);
        if (bookings.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy vé với mã giao dịch: " + txnRef);
        }

        Booking firstBooking = bookings.get(0);
        Customer customer = firstBooking.getCustomer();
        validateCancelableBookingGroup(bookings, customer, username);

        double ticketTotal = bookings.stream()
                .mapToDouble(b -> b.getTotal() != null ? b.getTotal() : 0.0)
                .sum();
        double snackTotal = bookingSnackRepo.findByTxnRef(txnRef).stream()
                .mapToDouble(BookingSnack::getSubtotal)
                .sum();
        double refundAmount = ticketTotal + snackTotal;

        for (Booking booking : bookings) {
            booking.setStatus(Booking.Status.CANCELLED);
            Seat seat = booking.getSeat();
            if (seat != null) {
                seat.setBooking(false);
                seatRepo.save(seat);
            }
        }
        bookingRepo.saveAll(bookings);

        int refundedPoints = pointService.refundPoints(customer, firstBooking, refundAmount);
        int availablePoints = pointService.getAvailablePoints(customer.getCustomerId());

        return Map.of(
                "message", "Hủy vé thành công! Bạn được hoàn " + refundedPoints + " điểm (hạn dùng 3 tháng).",
                "txnRef", txnRef,
                "cancelledBookings", bookings.size(),
                "ticketTotal", ticketTotal,
                "snackTotal", snackTotal,
                "refundAmount", refundAmount,
                "refundedPoints", refundedPoints,
                "totalAvailablePoints", availablePoints
        );
    }

    private void validateCancelableBookingGroup(List<Booking> bookings, Customer customer, String username) {
        if (customer == null || customer.getUser() == null) {
            throw new IllegalStateException("Vé này không có thông tin khách hàng.");
        }

        String ownerEmail = customer.getUser().getEmail();
        String ownerPhone = customer.getUser().getPhone();
        boolean isOwner = (ownerEmail != null && ownerEmail.equalsIgnoreCase(username))
                || (ownerPhone != null && ownerPhone.equalsIgnoreCase(username));
        if (!isOwner) {
            throw new SecurityException("Bạn không có quyền hủy vé này.");
        }

        for (Booking booking : bookings) {
            if (booking.getStatus() != Booking.Status.PAID) {
                throw new IllegalStateException("Chỉ có thể hủy vé đã thanh toán. Trạng thái hiện tại: "
                        + booking.getStatus());
            }
            if (booking.getShowtime() == null || booking.getShowtime().getStartTime() == null) {
                throw new IllegalStateException("Vé này không có thông tin giờ chiếu.");
            }
            LocalDateTime showtimeStart = booking.getShowtime().getStartTime();
            LocalDateTime deadline = showtimeStart.minusMinutes(30);
            if (LocalDateTime.now().isAfter(deadline)) {
                throw new IllegalStateException(
                        "Không thể hủy vé. Chỉ được hủy trước giờ chiếu ít nhất 30 phút. " +
                                "Giờ chiếu: " + showtimeStart);
            }
        }
    }

}
