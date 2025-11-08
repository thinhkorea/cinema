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

    @PostMapping("/create-payment")
    public ResponseEntity<?> createPayment(@RequestBody Map<String, Object> body) throws Exception {
        // Nếu FE đã truyền txnRef thì dùng luôn, không tạo mới
        String txnRef = String.valueOf(body.getOrDefault("txnRef", System.currentTimeMillis()));

        // Lấy tổng tiền
        long amount = ((Number) body.getOrDefault("amount", 100000)).longValue() * 100;
        String orderDesc = String.valueOf(body.getOrDefault("orderDescription", "Thanh toan ve xem phim"));
        String role = String.valueOf(body.getOrDefault("role", "customer")); // Thêm dòng này

        String createDate = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());

        // Nếu role = staff → thêm ?role=staff vào return URL
        String returnUrl = vnp_ReturnUrl;
        if ("staff".equalsIgnoreCase(role)) {
            if (returnUrl.contains("?")) {
                returnUrl += "&role=staff";
            } else {
                returnUrl += "?role=staff";
            }
        }

        Map<String, String> vnp = new HashMap<>();
        vnp.put("vnp_Version", "2.1.0");
        vnp.put("vnp_Command", "pay");
        vnp.put("vnp_TmnCode", vnp_TmnCode);
        vnp.put("vnp_Amount", String.valueOf(amount));
        vnp.put("vnp_CurrCode", "VND");
        vnp.put("vnp_TxnRef", txnRef); // Dùng chung txnRef với DB
        vnp.put("vnp_OrderInfo", orderDesc);
        vnp.put("vnp_OrderType", "billpayment");
        vnp.put("vnp_Locale", "vn");
        vnp.put("vnp_ReturnUrl", returnUrl); // Dùng returnUrl có thể có ?role=staff
        vnp.put("vnp_IpAddr", "127.0.0.1");
        vnp.put("vnp_CreateDate", createDate);

        // Sort và ký hash
        List<String> keys = new ArrayList<>(vnp.keySet());
        Collections.sort(keys);
        StringBuilder data = new StringBuilder();
        StringBuilder query = new StringBuilder();

        for (Iterator<String> it = keys.iterator(); it.hasNext();) {
            String k = it.next();
            String v = vnp.get(k);
            if (v != null && !v.isEmpty()) {
                String enc = URLEncoder.encode(v, StandardCharsets.US_ASCII);
                data.append(k).append('=').append(enc);
                query.append(k).append('=').append(enc);
                if (it.hasNext()) {
                    data.append('&');
                    query.append('&');
                }
            }
        }

        String secureHash = hmacSHA512(vnp_HashSecret, data.toString());
        String paymentUrl = vnp_Url + "?" + query +
                "&vnp_SecureHashType=HmacSHA512&vnp_SecureHash=" + secureHash;

        // Log kiểm tra
        System.out.println("VNPay URL (" + role + "): " + paymentUrl);

        // Trả về để FE có thể mở VNPay
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

            return ResponseEntity.status(302)
                    .header("Location", redirectUrl)
                    .build();
        }

        // Nếu thất bại
        if ("staff".equalsIgnoreCase(role)) {
            redirectUrl = "http://localhost:5173/staff/payment-result?vnp_ResponseCode=" + responseCode
                    + "&vnp_TxnRef=" + txnRef;
        } else {
            redirectUrl = "http://localhost:5173/payment-result?vnp_ResponseCode=" + responseCode
                    + "&vnp_TxnRef=" + txnRef;
        }

        System.out.println("VNPay thất bại, mã lỗi: " + responseCode);
        return ResponseEntity.status(302)
                .header("Location", redirectUrl)
                .build();
    }
}
