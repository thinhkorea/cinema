package com.example.cinema.config;

import com.example.cinema.dto.ApiErrorDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
@RestControllerAdvice
public class GlobalErrorHandler {

    private ResponseEntity<ApiErrorDTO> build(HttpStatus status, String error, String message, HttpServletRequest req) {
        return ResponseEntity.status(status)
                .body(ApiErrorDTO.builder()
                        .timestamp(Instant.now())
                        .status(status.value())
                        .error(error)
                        .message(message)
                        .path(req.getRequestURI())
                        .build());
    }

    // Validate @Valid fail
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorDTO> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst().map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse("Validation error");
        return build(HttpStatus.BAD_REQUEST, "Bad Request", msg, req);
    }

    // Lỗi nghiệp vụ bạn ném IllegalArgumentException
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorDTO> handleIllegalArg(IllegalArgumentException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), req);
    }

    // Lỗi quyền hạn ở service (ví dụ cancel vé của người khác)
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiErrorDTO> handleSecurity(SecurityException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), req);
    }

    // Trùng unique DB (đặt ghế trùng)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorDTO> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        String path = req.getRequestURI();
        String message = "Data integrity violation";
        if (path != null && path.contains("/admin/inventory/snacks/") && path.endsWith("/recipe")) {
            message = "Cong thuc co nguyen lieu trung hoac du lieu khong hop le.";
        } else if (path != null && path.startsWith("/api/movies")) {
            String detail = rootMessage(ex).toLowerCase();
            if (detail.contains("data too long") || detail.contains("too long for column")) {
                message = "Du lieu phim qua dai. Hay rut gon hoac khoi dong lai backend de cap nhat cot mo ta sang TEXT.";
            } else {
                message = "Du lieu phim khong hop le. Vui long kiem tra tieu de, thoi luong, trailer, poster va mo ta.";
            }
        }
        return build(HttpStatus.BAD_REQUEST, "Bad Request", message, req);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null || current.getMessage() == null ? "" : current.getMessage();
    }

    // Fallback
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDTO> handleOther(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", ex.getMessage(), req);
    }
}
