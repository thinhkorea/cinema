package com.example.cinema.service;

import com.example.cinema.domain.Booking;
import com.example.cinema.domain.BookingSnack;
import com.example.cinema.domain.User;
import com.example.cinema.repository.BookingSnackRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class TicketEmailService {

    private static final DateTimeFormatter SHOWTIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,###");
    private static final String SENDER_NAME = "CinemaAndJoy";

    private final ApplicationContext applicationContext;
    private final TicketPDFService ticketPDFService;
    private final BookingSnackRepository bookingSnackRepository;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    public TicketEmailService(ApplicationContext applicationContext,
            TicketPDFService ticketPDFService,
            BookingSnackRepository bookingSnackRepository) {
        this.applicationContext = applicationContext;
        this.ticketPDFService = ticketPDFService;
        this.bookingSnackRepository = bookingSnackRepository;
    }

    public void sendPaidTicketEmail(List<Booking> paidBookings) {
        if (paidBookings == null || paidBookings.isEmpty()) {
            return;
        }

        Booking first = paidBookings.get(0);
        User user = first.getCustomer() != null ? first.getCustomer().getUser() : null;
        String toEmail = user != null ? user.getEmail() : null;
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }

        List<Booking> sortedBookings = paidBookings.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(b -> b.getSeat().getSeatNumber()))
                .collect(Collectors.toList());

        String seats = sortedBookings.stream()
                .map(b -> b.getSeat().getSeatNumber())
                .collect(Collectors.joining(", "));

        double total = sortedBookings.stream()
                .mapToDouble(b -> b.getTotal() != null ? b.getTotal() : 0.0)
                .sum();

        List<BookingSnack> snacks = bookingSnackRepository.findByTxnRef(first.getTxnRef());
        double snackTotal = snacks.stream().mapToDouble(BookingSnack::getSubtotal).sum();

        String html = buildEmailHtml(first, seats, total, snacks, snackTotal);
        byte[] qrPngBytes = ticketPDFService.generateTxnQrPng(first, 220);

        try {
            sendTicketEmail(toEmail, first.getTxnRef(), html, qrPngBytes);
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể gửi email vé: " + ex.getMessage(), ex);
        }
    }

        private void sendTicketEmail(String toEmail, String txnRef, String html, byte[] qrPngBytes) throws Exception {
        Class<?> javaMailSenderClass = Class.forName("org.springframework.mail.javamail.JavaMailSender");
        Object mailSenderBean = applicationContext.getBean(javaMailSenderClass);

        Method createMimeMessageMethod = javaMailSenderClass.getMethod("createMimeMessage");
        Object mimeMessage = createMimeMessageMethod.invoke(mailSenderBean);

        Class<?> mimeMessageClass = Class.forName("jakarta.mail.internet.MimeMessage");
        Class<?> mimeMessageHelperClass = Class.forName("org.springframework.mail.javamail.MimeMessageHelper");
        Object helper = mimeMessageHelperClass
            .getConstructor(mimeMessageClass, boolean.class, String.class)
            .newInstance(mimeMessage, true, "UTF-8");

        if (mailFrom != null && !mailFrom.isBlank()) {
            try {
                invokeHelperMethod(
                        mimeMessageHelperClass,
                        helper,
                        "setFrom",
                        new Class<?>[] { String.class, String.class },
                        mailFrom,
                        SENDER_NAME);
            } catch (NoSuchMethodException ignored) {
                invokeHelperMethod(mimeMessageHelperClass, helper, "setFrom", new Class<?>[] { String.class },
                        mailFrom);
            }
        }
        invokeHelperMethod(mimeMessageHelperClass, helper, "setTo", new Class<?>[] { String.class }, toEmail);
        invokeHelperMethod(mimeMessageHelperClass, helper, "setSubject", new Class<?>[] { String.class },
                "[CinemaAndJoy] Vé của bạn đã thanh toán thành công - " + txnRef);
        invokeHelperMethod(mimeMessageHelperClass, helper, "setText", new Class<?>[] { String.class, boolean.class },
            html, true);

        Class<?> inputStreamSourceClass = Class.forName("org.springframework.core.io.InputStreamSource");
        Object qrByteArrayResource = Class
            .forName("org.springframework.core.io.ByteArrayResource")
            .getConstructor(byte[].class)
            .newInstance(qrPngBytes);

        invokeHelperMethod(
            mimeMessageHelperClass,
            helper,
            "addInline",
            new Class<?>[] { String.class, inputStreamSourceClass, String.class },
            "txnQrImage",
            qrByteArrayResource,
            "image/png");

        Method sendMethod = javaMailSenderClass.getMethod("send", mimeMessageClass);
        sendMethod.invoke(mailSenderBean, mimeMessage);
        }

        private void invokeHelperMethod(Class<?> helperClass, Object helper, String methodName,
            Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = helperClass.getMethod(methodName, parameterTypes);
        method.invoke(helper, args);
        }

    private String buildEmailHtml(Booking first, String seats, double total, List<BookingSnack> snacks, double snackTotal) {
        String fullName = first.getCustomer() != null && first.getCustomer().getUser() != null
                ? first.getCustomer().getUser().getFullName()
                : "bạn";
        String movieTitle = first.getShowtime().getMovie().getTitle();
        String room = first.getShowtime().getRoom().getRoomName();
        String showtimeText = first.getShowtime().getStartTime().format(SHOWTIME_FORMAT);
        String paymentMethod = first.getPaymentMethod() != null ? first.getPaymentMethod() : "VNPAY";
        String snacksHtml = buildSnacksHtml(snacks);
        String ticketTotalText = MONEY_FORMAT.format(total);
        String snackTotalText = MONEY_FORMAT.format(snackTotal);
        String grandTotalText = MONEY_FORMAT.format(total + snackTotal);

        return """
                <div style=\"font-family:Arial,sans-serif;line-height:1.5;color:#222;\">
                  <h2 style=\"margin:0 0 12px;color:#0b5ed7;\">Thanh toán thành công</h2>
                  <p>Xin chào <strong>%s</strong>, cảm ơn bạn đã đặt vé tại rạp.</p>
                  <p>Dưới đây là thông tin vé của bạn:</p>
                  <ul>
                    <li><strong>Mã giao dịch:</strong> %s</li>
                    <li><strong>Phim:</strong> %s</li>
                    <li><strong>Phòng chiếu:</strong> %s</li>
                    <li><strong>Suất chiếu:</strong> %s</li>
                    <li><strong>Ghế:</strong> %s</li>
                    <li><strong>Phương thức thanh toán:</strong> %s</li>
                                        <li><strong>Tổng tiền vé:</strong> %s VND</li>
                  </ul>
                                    %s
                                    <p><strong>Tổng bắp nước:</strong> %s VND</p>
                                    <p><strong>Tổng thanh toán:</strong> %s VND</p>
                                    <p>Vui lòng dùng mã QR bên dưới để check-in vào rạp.</p>
                                    <div style="margin:14px 0;text-align:center;">
                                        <p style="margin:0 0 8px;"><strong>Mã QR giao dịch (txnRef)</strong></p>
                                        <img src="cid:txnQrImage" alt="QR giao dịch" style="width:180px;height:180px;border:1px solid #ddd;padding:6px;border-radius:8px;" />
                                    </div>
                  <p>Vui lòng đến trước giờ chiếu ít nhất 10 phút.</p>
                </div>
                """.formatted(
                fullName,
                first.getTxnRef(),
                movieTitle,
                room,
                showtimeText,
                seats,
                paymentMethod,
                ticketTotalText,
                snacksHtml,
                snackTotalText,
                grandTotalText);
    }

    private String buildSnacksHtml(List<BookingSnack> snacks) {
        if (snacks == null || snacks.isEmpty()) {
            return "<p><strong>Bắp nước:</strong> Không có</p>";
        }

        LinkedHashMap<String, double[]> grouped = new LinkedHashMap<>();
        for (BookingSnack item : snacks) {
            String name = item.getSnack() != null ? item.getSnack().getSnackName() : "Snack";
            grouped.putIfAbsent(name, new double[] { 0, 0 });
            grouped.get(name)[0] += item.getQuantity() != null ? item.getQuantity() : 0;
            grouped.get(name)[1] += item.getSubtotal() != null ? item.getSubtotal() : 0;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<p><strong>Bắp nước đi kèm:</strong></p><ul>");
        grouped.forEach((name, values) -> {
            String qty = String.valueOf((int) values[0]);
            String subtotal = MONEY_FORMAT.format(values[1]);
            sb.append("<li>")
                    .append(name)
                    .append(" x")
                    .append(qty)
                    .append(" - ")
                    .append(subtotal)
                    .append(" VND</li>");
        });
        sb.append("</ul>");
        return sb.toString();
    }
}
