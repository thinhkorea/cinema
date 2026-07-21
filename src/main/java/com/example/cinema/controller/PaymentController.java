package com.example.cinema.controller;

import com.example.cinema.service.BookingService;
import com.example.cinema.service.SnackOrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final BookingService bookingService;
    private final SnackOrderService snackOrderService;

    public PaymentController(BookingService bookingService, SnackOrderService snackOrderService) {
        this.bookingService = bookingService;
        this.snackOrderService = snackOrderService;
    }

    @Value("${vnp_TmnCode}")
    private String vnpTmnCode;

    @Value("${vnp_HashSecret}")
    private String vnpHashSecret;

    @Value("${vnp_Url}")
    private String vnpUrl;

    @Value("${vnp_ReturnUrl}")
    private String vnpReturnUrl;

    private static String hmacSHA512(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac.init(secretKey);
            byte[] bytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error while calculating HMAC-SHA512", e);
        }
    }

    private static boolean secureEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                right.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private boolean isValidVnpaySignature(Map<String, String> params) {
        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) {
            return false;
        }

        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);

        StringBuilder data = new StringBuilder();
        boolean first = true;
        for (String key : keys) {
            if ("vnp_SecureHash".equals(key) || "vnp_SecureHashType".equals(key)) {
                continue;
            }

            String value = params.get(key);
            if (value == null || value.isBlank() || !key.startsWith("vnp_")) {
                continue;
            }

            if (!first) {
                data.append('&');
            }
            data.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            first = false;
        }

        String calculatedHash = hmacSHA512(vnpHashSecret.trim(), data.toString());
        return secureEquals(calculatedHash, receivedHash);
    }

    private String normalizeFlow(String flow) {
        return "snack-order".equalsIgnoreCase(flow) ? "snack-order" : "booking";
    }

    private String createPaymentToken(String txnRef, String responseCode, String flow) {
        return hmacSHA512(vnpHashSecret.trim(), txnRef + "|" + responseCode + "|" + normalizeFlow(flow));
    }

    private boolean isValidPaymentToken(String txnRef, String responseCode, String flow, String paymentToken) {
        if (txnRef == null || txnRef.isBlank() || responseCode == null || paymentToken == null || paymentToken.isBlank()) {
            return false;
        }
        return secureEquals(createPaymentToken(txnRef, responseCode, flow), paymentToken);
    }

    private static String sanitizeOrderInfo(String input) {
        if (input == null || input.isBlank()) {
            return "Thanh toan ve xem phim";
        }

        String noAccent = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        String safe = noAccent
                .replaceAll("[^A-Za-z0-9 _.,:/-]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (safe.isEmpty()) {
            return "Thanh toan ve xem phim";
        }

        return safe.length() > 255 ? safe.substring(0, 255) : safe;
    }

    @PostMapping("/create-payment")
    public ResponseEntity<?> createPayment(@RequestBody Map<String, Object> body) {
        String txnRef = String.valueOf(body.getOrDefault("txnRef", System.currentTimeMillis()));
        long amount = ((Number) body.getOrDefault("amount", 100000)).longValue() * 100;
        String orderDesc = sanitizeOrderInfo(
                String.valueOf(body.getOrDefault("orderDescription", "Thanh toan ve xem phim")));
        String role = String.valueOf(body.getOrDefault("role", "customer"));
        String flow = String.valueOf(body.getOrDefault("flow", "booking"));
        String client = String.valueOf(body.getOrDefault("client", "web"));

        TimeZone vnTimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");
        Calendar calendar = Calendar.getInstance(vnTimeZone);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(vnTimeZone);
        String createDate = formatter.format(calendar.getTime());
        calendar.add(Calendar.MINUTE, 15);
        String expireDate = formatter.format(calendar.getTime());

        String returnUrl = vnpReturnUrl
                + (vnpReturnUrl.contains("?") ? "&" : "?")
                + "role=" + URLEncoder.encode(role, StandardCharsets.UTF_8)
                + "&flow=" + URLEncoder.encode(flow, StandardCharsets.UTF_8)
                + "&client=" + URLEncoder.encode(client, StandardCharsets.UTF_8);

        Map<String, String> vnp = new HashMap<>();
        vnp.put("vnp_Version", "2.1.0");
        vnp.put("vnp_Command", "pay");
        vnp.put("vnp_TmnCode", vnpTmnCode);
        vnp.put("vnp_Amount", String.valueOf(amount));
        vnp.put("vnp_CurrCode", "VND");
        vnp.put("vnp_TxnRef", txnRef);
        vnp.put("vnp_OrderInfo", orderDesc);
        vnp.put("vnp_OrderType", "billpayment");
        vnp.put("vnp_Locale", "vn");
        vnp.put("vnp_ReturnUrl", returnUrl);
        vnp.put("vnp_IpAddr", "127.0.0.1");
        vnp.put("vnp_CreateDate", createDate);
        vnp.put("vnp_ExpireDate", expireDate);

        List<String> keys = new ArrayList<>(vnp.keySet());
        Collections.sort(keys);
        StringBuilder data = new StringBuilder();
        StringBuilder query = new StringBuilder();

        boolean first = true;
        for (String key : keys) {
            String value = vnp.get(key);
            if (value != null && !value.isEmpty()) {
                if (!first) {
                    data.append('&');
                    query.append('&');
                }
                String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8);
                String encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8);
                data.append(encodedKey).append('=').append(encodedValue);
                query.append(encodedKey).append('=').append(encodedValue);
                first = false;
            }
        }

        String vnpSecureHash = hmacSHA512(vnpHashSecret.trim(), data.toString());
        String paymentUrl = vnpUrl + "?" + query
                + "&vnp_SecureHashType=HmacSHA512&vnp_SecureHash=" + vnpSecureHash;

        return ResponseEntity.ok(Map.of(
                "paymentUrl", paymentUrl,
                "txnRef", txnRef,
                "role", role,
                "flow", flow,
                "client", client));
    }

    @GetMapping("/vnpay-return")
    public ResponseEntity<?> handleReturn(@RequestParam Map<String, String> params) {
        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");
        String txnRef = params.get("vnp_TxnRef");
        String role = params.get("role");
        String flow = normalizeFlow(params.get("flow"));
        String client = params.get("client");

        boolean snackOrderFlow = "snack-order".equalsIgnoreCase(flow);
        boolean mobileClient = "mobile".equalsIgnoreCase(client);

        if (!isValidVnpaySignature(params)) {
            responseCode = "97";
            System.out.println("VNPay invalid signature, txnRef=" + txnRef);
        } else if (txnRef == null || txnRef.isBlank()) {
            responseCode = "99";
            System.out.println("VNPay missing txnRef");
        } else if (transactionStatus != null && !"00".equals(transactionStatus)) {
            responseCode = transactionStatus;
            System.out.println("VNPay transaction failed, txnRef=" + txnRef + ", status=" + transactionStatus);
        }

        String paymentToken = "00".equals(responseCode) ? createPaymentToken(txnRef, responseCode, flow) : null;

        String redirectUrl = "00".equals(responseCode)
                ? buildSuccessRedirect(mobileClient, snackOrderFlow, role, responseCode, txnRef, flow, paymentToken)
                : buildFailedRedirect(mobileClient, snackOrderFlow, role, responseCode, txnRef, flow);

        return ResponseEntity.status(302)
                .header("Location", redirectUrl)
                .build();
    }

    @PostMapping("/confirm-vnpay")
    public ResponseEntity<?> confirmVnpayPayment(@RequestBody Map<String, String> body) {
        String txnRef = body.get("txnRef");
        String responseCode = body.get("responseCode");
        String flow = normalizeFlow(body.get("flow"));
        String paymentToken = body.get("paymentToken");

        if (!"00".equals(responseCode) || !isValidPaymentToken(txnRef, responseCode, flow, paymentToken)) {
            return ResponseEntity.status(403).body(Map.of("error", "Thong tin xac nhan thanh toan khong hop le."));
        }

        try {
            if ("snack-order".equals(flow)) {
                snackOrderService.markPaidByOrderCode(txnRef, "VNPAY");
            } else {
                bookingService.markPaidByTxn(txnRef, "VNPAY");
            }
            return ResponseEntity.ok(Map.of("message", "OK", "txnRef", txnRef, "flow", flow));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    private String resultQuery(String responseCode, String txnRef, String flow, String paymentToken) {
        StringBuilder query = new StringBuilder();
        query.append("vnp_ResponseCode=").append(URLEncoder.encode(responseCode == null ? "" : responseCode, StandardCharsets.UTF_8));
        query.append("&vnp_TxnRef=").append(URLEncoder.encode(txnRef == null ? "" : txnRef, StandardCharsets.UTF_8));
        query.append("&flow=").append(URLEncoder.encode(normalizeFlow(flow), StandardCharsets.UTF_8));
        if (paymentToken != null && !paymentToken.isBlank()) {
            query.append("&paymentToken=").append(URLEncoder.encode(paymentToken, StandardCharsets.UTF_8));
        }
        return query.toString();
    }

    private String buildSuccessRedirect(
            boolean mobileClient,
            boolean snackOrderFlow,
            String role,
            String responseCode,
            String txnRef,
            String flow,
            String paymentToken) {
        String query = resultQuery(responseCode, txnRef, flow, paymentToken);
        if (mobileClient && snackOrderFlow) {
            return "cinemaapp:///snack-order-result?" + query;
        }
        if (mobileClient) {
            return "cinemaapp:///payment-result?" + query;
        }
        if (snackOrderFlow) {
            return "http://localhost:5173/snack-order-result?" + query;
        }
        if ("staff".equalsIgnoreCase(role)) {
            return "http://localhost:5173/staff/payment-result?" + query;
        }
        return "http://localhost:5173/payment-result?" + query;
    }

    private String buildFailedRedirect(
            boolean mobileClient,
            boolean snackOrderFlow,
            String role,
            String responseCode,
            String txnRef,
            String flow) {
        String query = resultQuery(responseCode, txnRef, flow, null);
        if (mobileClient && snackOrderFlow) {
            return "cinemaapp:///snack-order-result?" + query;
        }
        if (mobileClient) {
            return "cinemaapp:///payment-result?" + query;
        }
        if (snackOrderFlow) {
            return "http://localhost:5173/snack-order-result?" + query;
        }
        if ("staff".equalsIgnoreCase(role)) {
            return "http://localhost:5173/staff/payment-result?" + query;
        }
        return "http://localhost:5173/payment-result?" + query;
    }
}
