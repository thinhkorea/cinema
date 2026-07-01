package com.example.cinema.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Locale;

@Component
public class CinemaBotIntentRouter {

    private final CinemaBotLexicon lexicon;
    private final CinemaBotClarificationService clarificationService;

    public CinemaBotIntentRouter(CinemaBotLexicon lexicon, CinemaBotClarificationService clarificationService) {
        this.lexicon = lexicon;
        this.clarificationService = clarificationService;
    }

    public CinemaBotService.QueryAnalysis route(String userMessage, CinemaBotService.QueryAnalysis llmAnalysis) {
        String normalized = normalize(userMessage);
        CinemaBotService.QueryAnalysis result = copyOrCreate(llmAnalysis);

        if (isBookingQuestion(normalized)) {
            result.intent = "BOOKING_INFO";
            return result;
        }

        if (isExplicitShowtimeQuestion(normalized) || isImplicitShowtimeQuestion(normalized)) {
            result.intent = "SHOWTIMES";
            addDateFilterIfNeeded(result, normalized);
            return result;
        }

        if (isMovieListQuestion(normalized) || isMovieSearchQuestion(normalized)) {
            result.intent = "MOVIES";
            addMovieStatusFilterIfNeeded(result, normalized);
            addMovieGenreFilterIfNeeded(result, normalized);
            return result;
        }

        if (isSnackQuestion(normalized)) {
            result.intent = "SNACKS";
            return result;
        }

        if (isLoyaltyQuestion(normalized)) {
            result.intent = "LOYALTY";
            return result;
        }

        if (isVoucherQuestion(normalized)) {
            result.intent = "VOUCHERS";
            return result;
        }

        return result;
    }

    public boolean isShowtimeSuggestionRequest(String userMessage) {
        String normalized = normalize(userMessage);
        return lexicon.containsAny(normalized,
                "suất chiếu",
                "lịch chiếu",
                "giờ chiếu",
                "đặt vé",
                "chọn ghế",
                "mua vé")
                || isExplicitShowtimeQuestion(normalized)
                || isImplicitShowtimeQuestion(normalized);
    }

    public boolean isQuickGreeting(String userMessage) {
        return lexicon.equalsAny(normalize(userMessage),
                "chào", "chào bạn", "chào bot",
                "xin chào", "xin chào bạn", "xin chào bot",
                "hi", "hello");
    }

    public boolean isQuickGoodbyeOrThanks(String userMessage) {
        return lexicon.equalsAny(normalize(userMessage),
                "tạm biệt", "cảm ơn", "bye", "goodbye", "tks", "thanks");
    }

    public boolean isCurrentUserIdentityQuestion(String userMessage) {
        String normalized = normalize(userMessage);
        return lexicon.containsAny(normalized,
                "tôi là ai",
                "mình là ai",
                "biết tôi không",
                "biết mình không")
                || (lexicon.contains(normalized, "biết") && lexicon.contains(normalized, "là ai"));
    }

    public String resolveClarificationMessage(String userMessage) {
        return clarificationService.resolveClarificationMessage(userMessage);
    }

    public boolean isPointExpiryQuestion(String userMessage) {
        String normalized = normalize(userMessage);
        return clarificationService.isExpiryQuestion(normalized) && lexicon.contains(normalized, "điểm");
    }

    public boolean isExpiryFollowUpQuestion(String userMessage) {
        return clarificationService.isExpiryQuestion(userMessage);
    }

    public String resolveContextualIntent(String userMessage, String previousIntent) {
        if (previousIntent == null || previousIntent.isBlank()) {
            return null;
        }
        String normalized = normalize(userMessage);
        String normalizedPreviousIntent = previousIntent.toUpperCase(Locale.ROOT);
        if (clarificationService.hasReferenceTerm(normalized)
                && clarificationService.isExpiryQuestion(normalized)
                && !lexicon.contains(normalized, "voucher")) {
            return normalizedPreviousIntent;
        }
        if (lexicon.containsAny(normalized, "hạn sử dụng", "hạn dùng")) {
            if ("LOYALTY".equals(normalizedPreviousIntent) && !lexicon.contains(normalized, "voucher")) {
                return "LOYALTY";
            }
        }
        return null;
    }

    public String normalize(String value) {
        return lexicon.normalize(value);
    }

    boolean isImplicitShowtimeQuestion(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return false;
        }
        boolean hasMovieTerm = lexicon.contains(normalizedMessage, "phim");
        boolean hasShowTerm = lexicon.containsAny(normalizedMessage,
                "chiếu",
                "đang chiếu",
                "có phim nào",
                "rạp có gì");
        return hasMovieTerm && hasShowTerm && hasDateTerm(normalizedMessage);
    }

    boolean isExplicitShowtimeQuestion(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return false;
        }
        return lexicon.containsAny(normalizedMessage,
                "suất chiếu",
                "lịch chiếu",
                "giờ chiếu",
                "phòng chiếu",
                "chiếu lúc",
                "mấy giờ chiếu");
    }

    boolean isMovieListQuestion(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return false;
        }
        return lexicon.equalsAny(normalizedMessage,
                "phim đang chiếu",
                "phim sắp chiếu",
                "danh sách phim",
                "danh sách phim đang chiếu",
                "danh sách phim sắp chiếu")
                || lexicon.containsAny(normalizedMessage,
                "phim đang chiếu",
                "phim sắp chiếu",
                "phim nào đang chiếu",
                "phim nào sắp chiếu",
                "đang có phim gì");
    }

    boolean isMovieSearchQuestion(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return false;
        }
        return lexicon.contains(normalizedMessage, "phim")
                && (lexicon.containsAny(normalizedMessage,
                "thể loại",
                "loại phim",
                "đang được chiếu",
                "đang chiếu")
                || lexicon.resolveMovieGenre(normalizedMessage) != null);
    }

    private boolean isSnackQuestion(String normalizedMessage) {
        return lexicon.containsAny(normalizedMessage,
                "bắp nước",
                "combo",
                "đồ ăn",
                "đồ uống",
                "nước gì",
                "bắp gì",
                "snack");
    }

    private boolean isBookingQuestion(String normalizedMessage) {
        return lexicon.containsAny(normalizedMessage,
                "vé của tôi",
                "vé đã đặt",
                "lịch sử đặt vé",
                "booking của tôi",
                "tôi đã đặt vé",
                "hủy vé",
                "huỷ vé",
                "hủy booking",
                "huỷ booking",
                "hủy đơn",
                "huỷ đơn",
                "cách hủy",
                "cách huỷ",
                "muốn hủy vé",
                "muốn huỷ vé");
    }

    public boolean isBookingCancellationQuestion(String userMessage) {
        String normalized = normalize(userMessage);
        return lexicon.containsAny(normalized,
                "hủy vé",
                "huỷ vé",
                "hủy booking",
                "huỷ booking",
                "hủy đơn",
                "huỷ đơn",
                "cách hủy",
                "cách huỷ",
                "muốn hủy vé",
                "muốn huỷ vé");
    }

    private boolean isLoyaltyQuestion(String normalizedMessage) {
        return lexicon.containsAny(normalizedMessage,
                "điểm của tôi",
                "điểm tích lũy",
                "điểm thành viên")
                || isPointExpiryQuestion(normalizedMessage);
    }

    private boolean isVoucherQuestion(String normalizedMessage) {
        return lexicon.containsAny(normalizedMessage,
                "voucher",
                "mã giảm giá",
                "khuyến mãi",
                "ưu đãi");
    }

    private boolean hasDateTerm(String normalizedMessage) {
        return lexicon.containsAny(normalizedMessage,
                "hôm nay",
                "tối nay",
                "sáng nay",
                "trưa nay",
                "chiều nay",
                "hôm qua",
                "tối qua",
                "ngày mai",
                "tối mai")
                || normalizedMessage.matches(".*\\b\\d{1,2}[/-]\\d{1,2}([/-]\\d{2,4})?\\b.*")
                || normalizedMessage.matches(".*\\b\\d{4}-\\d{1,2}-\\d{1,2}\\b.*");
    }

    private CinemaBotService.QueryAnalysis copyOrCreate(CinemaBotService.QueryAnalysis source) {
        CinemaBotService.QueryAnalysis result = new CinemaBotService.QueryAnalysis();
        result.intent = source != null && source.intent != null ? source.intent : "GENERAL";
        result.keywords = source != null && source.keywords != null ? new ArrayList<>(source.keywords) : new ArrayList<>();
        result.filters = source != null && source.filters != null ? new ArrayList<>(source.filters) : new ArrayList<>();
        return result;
    }

    private void addMovieStatusFilterIfNeeded(CinemaBotService.QueryAnalysis analysis, String normalizedMessage) {
        if (hasFilter(analysis, "status")) {
            return;
        }
        if (lexicon.containsAny(normalizedMessage, "đang chiếu", "đang được chiếu")) {
            analysis.filters.add("status:NOW_SHOWING");
        } else if (lexicon.contains(normalizedMessage, "sắp chiếu")) {
            analysis.filters.add("status:COMING_SOON");
        }
    }

    private void addMovieGenreFilterIfNeeded(CinemaBotService.QueryAnalysis analysis, String normalizedMessage) {
        if (hasFilter(analysis, "genre")) {
            return;
        }
        String genre = lexicon.resolveMovieGenre(normalizedMessage);
        if (genre != null) {
            analysis.filters.add("genre:" + genre);
        }
    }

    private void addDateFilterIfNeeded(CinemaBotService.QueryAnalysis analysis, String normalizedMessage) {
        if (hasFilter(analysis, "date")) {
            return;
        }
        String dateValue = resolveDateFilterValue(normalizedMessage);
        if (dateValue != null) {
            analysis.filters.add("date:" + dateValue);
        }
    }

    private String resolveDateFilterValue(String normalizedMessage) {
        if (lexicon.containsAny(normalizedMessage, "hôm qua", "tối qua")) {
            return normalize("hôm qua");
        }
        if (lexicon.containsAny(normalizedMessage, "hôm nay", "tối nay", "sáng nay", "trưa nay", "chiều nay")) {
            return normalize("hôm nay");
        }
        if (lexicon.containsAny(normalizedMessage, "ngày mai", "tối mai")) {
            return normalize("ngày mai");
        }
        return null;
    }

    private boolean hasFilter(CinemaBotService.QueryAnalysis analysis, String key) {
        if (analysis.filters == null) {
            analysis.filters = new ArrayList<>();
            return false;
        }
        String prefix = key + ":";
        return analysis.filters.stream().anyMatch(filter -> filter != null && filter.startsWith(prefix));
    }
}
