package com.example.cinema.controller;

import com.example.cinema.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.text.Normalizer;
import java.util.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Value("${vnp_TmnCode}")
    private String vnp_TmnCode;

    @Value("${vnp_HashSecret}")
    private String vnp_HashSecret;

    @Value("${vnp_Url}")
    private String vnp_Url;

    @Value("${vnp_ReturnUrl}")
    private String vnp_ReturnUrl;

    private final BookingRepository bookingRepo;

    public PaymentController(BookingRepository bookingRepo) {
        this.bookingRepo = bookingRepo;
    }

    // HMAC-SHA512 đúng chuẩn VNPay
    private static String hmacSHA512(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac.init(secretKey);
            byte[] bytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error while calculating HMAC-SHA512", e);
        }
    }

    private static String sanitizeOrderInfo(String input) {
        if (input == null || input.isBlank()) {
            return "Thanh toan ve xem phim";
        }

        String noAccent = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        // VNPay order info should stay simple ASCII to avoid canonicalization mismatches.
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
    public ResponseEntity<?> createPayment(@RequestBody Map<String, Object> body) throws Exception {
        String txnRef = String.valueOf(body.getOrDefault("txnRef", System.currentTimeMillis()));
        long amount = ((Number) body.getOrDefault("amount", 100000)).longValue() * 100;
        String orderDesc = sanitizeOrderInfo(
                String.valueOf(body.getOrDefault("orderDescription", "Thanh toan ve xem phim"))
        );
        String role = String.valueOf(body.getOrDefault("role", "customer"));

        TimeZone vnTimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");
        Calendar calendar = Calendar.getInstance(vnTimeZone);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(vnTimeZone);
        String createDate = formatter.format(calendar.getTime());
        calendar.add(Calendar.MINUTE, 15);
        String expireDate = formatter.format(calendar.getTime());

        String returnUrl = vnp_ReturnUrl
                + (vnp_ReturnUrl.contains("?") ? "&" : "?")
                + "role=" + URLEncoder.encode(role, StandardCharsets.UTF_8);

        Map<String, String> vnp = new HashMap<>();
        vnp.put("vnp_Version", "2.1.0");
        vnp.put("vnp_Command", "pay");
        vnp.put("vnp_TmnCode", vnp_TmnCode);
        vnp.put("vnp_Amount", String.valueOf(amount));
        vnp.put("vnp_CurrCode", "VND");
        vnp.put("vnp_TxnRef", txnRef);
        vnp.put("vnp_OrderInfo", orderDesc);
        vnp.put("vnp_OrderType", "billpayment");
        vnp.put("vnp_Locale", "vn");
        vnp.put("vnp_ReturnUrl", returnUrl);  // ← URL sạch, không có ?role=staff
        vnp.put("vnp_IpAddr", "127.0.0.1");
        vnp.put("vnp_CreateDate", createDate);
        vnp.put("vnp_ExpireDate", expireDate);

        // Sort và ký hash
        List<String> keys = new ArrayList<>(vnp.keySet());
        Collections.sort(keys);
        StringBuilder data = new StringBuilder();
        StringBuilder query = new StringBuilder();

        boolean first = true;
        for (String k : keys) {
            String v = vnp.get(k);
            if (v != null && !v.isEmpty()) {
                if (!first) {
                    data.append('&');
                    query.append('&');
                }
                String encodedKey = URLEncoder.encode(k, StandardCharsets.UTF_8);
                String encodedValue = URLEncoder.encode(v, StandardCharsets.UTF_8);
                data.append(encodedKey).append('=').append(encodedValue);
                query.append(encodedKey).append('=').append(encodedValue);
                first = false;
            }
        }

        String vnpSecureHash = hmacSHA512(vnp_HashSecret.trim(), data.toString());
        String paymentUrl = vnp_Url + "?" + query +
                "&vnp_SecureHashType=HmacSHA512&vnp_SecureHash=" + vnpSecureHash;

        System.out.println("Raw data for hash: " + data.toString());
        System.out.println("Generated SecureHash: " + vnpSecureHash);
        System.out.println("VNPay URL (" + role + "): " + paymentUrl);

        return ResponseEntity.ok(Map.of(
                "paymentUrl", paymentUrl,
                "txnRef", txnRef,
                "role", role));
    }

    @GetMapping("/vnpay-return")
    public ResponseEntity<?> handleReturn(@RequestParam Map<String, String> params) {
        String responseCode = params.get("vnp_ResponseCode");
        String txnRef = params.get("vnp_TxnRef");
        String role = params.get("role");

        String redirectUrl;

        if ("00".equals(responseCode)) {
            System.out.println("VNPay giao dịch thành công, mã đơn hàng: " + txnRef);

            // Nếu là nhân viên → redirect về staff/payment-result
            if ("staff".equalsIgnoreCase(role)) {
                redirectUrl = "http://localhost:5173/staff/payment-result?vnp_ResponseCode=00&vnp_TxnRef=" + txnRef;
            } else {
                // Mặc định là khách hàng
                redirectUrl = "http://localhost:5173/payment-result?vnp_ResponseCode=00&vnp_TxnRef=" + txnRef;
            }
        } else {
            System.out.println("VNPay thất bại, mã lỗi: " + responseCode);
            
            // Nếu thất bại
            if ("staff".equalsIgnoreCase(role)) {
                redirectUrl = "http://localhost:5173/staff/payment-result?vnp_ResponseCode=" + responseCode
                        + "&vnp_TxnRef=" + txnRef;
            } else {
                redirectUrl = "http://localhost:5173/payment-result?vnp_ResponseCode=" + responseCode
                        + "&vnp_TxnRef=" + txnRef;
            }
        }

        return ResponseEntity.status(302)
                .header("Location", redirectUrl)
                .build();
    }
}
