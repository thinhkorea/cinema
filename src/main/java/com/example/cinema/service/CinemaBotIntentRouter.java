package com.example.cinema.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CinemaBotIntentRouter {

    private final CinemaBotLexicon lexicon;
    private final CinemaBotClarificationService clarificationService;

    public CinemaBotIntentRouter(CinemaBotLexicon lexicon, CinemaBotClarificationService clarificationService) {
        this.lexicon = lexicon;
        this.clarificationService = clarificationService;
    }

    public IntentDecision decide(String userMessage, CinemaBotService.QueryAnalysis llmAnalysis) {
        String normalized = normalize(userMessage);
        CinemaBotService.QueryAnalysis result = copyOrCreate(llmAnalysis);

        if (isSecurityRequest(normalized)) {
            result.intent = "SECURITY_REQUEST";
            return new IntentDecision(
                    CinemaBotIntent.SECURITY_REQUEST,
                    1.0,
                    extractEntities(result, normalized),
                    List.of(),
                    false,
                    false,
                    buildSecurityRefusalReply(normalized)
            );
        }

        if (isIncompleteBookingAction(normalized)) {
            result.intent = "BOOKING_INFO";
            return new IntentDecision(
                    CinemaBotIntent.BOOKING_INFO,
                    0.95,
                    extractEntities(result, normalized),
                    missingBookingFields(normalized),
                    true,
                    false,
                    buildBookingClarificationReply()
            );
        }

        result = route(userMessage, result);
        CinemaBotIntent intent = CinemaBotIntent.from(result.intent);
        boolean deterministic = isDeterministicIntent(intent, result);
        boolean hasMissingFields = result.missingFields != null && !result.missingFields.isEmpty();
        boolean allowedToQuery = !hasMissingFields && (result.allowedToQuery == null || result.allowedToQuery);
        return new IntentDecision(
                intent,
                resolveConfidence(result.confidence, deterministic ? 0.9 : 0.35),
                extractEntities(result, normalized),
                result.missingFields,
                requiresLogin(intent) || Boolean.TRUE.equals(result.requiresLogin),
                allowedToQuery,
                allowedToQuery ? null : buildMissingFieldsReply(result.missingFields)
        );
    }

    public CinemaBotService.QueryAnalysis route(String userMessage, CinemaBotService.QueryAnalysis llmAnalysis) {
        String normalized = normalize(userMessage);
        CinemaBotService.QueryAnalysis result = copyOrCreate(llmAnalysis);

        if (isBookingQuestion(normalized)) {
            result.intent = "BOOKING_INFO";
            return result;
        }

        if (isMovieRecommendationQuestion(normalized)) {
            result.intent = "MOVIES";
            if (!hasFilter(result, "status")) {
                result.filters.add("status:NOW_SHOWING");
            }
            addMovieAliasKeywordIfNeeded(result, normalized);
            addMovieMoodFilterIfNeeded(result, normalized);
            return result;
        }

        if (isExplicitShowtimeQuestion(normalized) || isImplicitShowtimeQuestion(normalized)) {
            result.intent = "SHOWTIMES";
            addDateFilterIfNeeded(result, normalized);
            addMovieAliasKeywordIfNeeded(result, normalized);
            addMovieGenreFilterIfNeeded(result, normalized);
            addMovieMoodFilterIfNeeded(result, normalized);
            addPriceFilterIfNeeded(result, normalized, "price_max");
            return result;
        }

        boolean movieAvailabilityQuestion = isMovieAvailabilityQuestion(normalized);
        if (movieAvailabilityQuestion || isMovieListQuestion(normalized) || isMovieSearchQuestion(normalized)) {
            result.intent = "MOVIES";
            if (movieAvailabilityQuestion && !hasFilter(result, "status")) {
                result.filters.add("status:NOW_SHOWING");
            }
            addMovieStatusFilterIfNeeded(result, normalized);
            addMovieAliasKeywordIfNeeded(result, normalized);
            addMovieGenreFilterIfNeeded(result, normalized);
            addMovieMoodFilterIfNeeded(result, normalized);
            return result;
        }

        if (isSnackQuestion(normalized)) {
            result.intent = "SNACKS";
            addPriceFilterIfNeeded(result, normalized, "price_max");
            addSnackCategoryFilterIfNeeded(result, normalized);
            return result;
        }

        if (isLoyaltyQuestion(normalized)) {
            result.intent = "LOYALTY";
            return result;
        }

        if (isVoucherQuestion(normalized)) {
            result.intent = "VOUCHERS";
            addPriceFilterIfNeeded(result, normalized, "price_min");
            return result;
        }

        addContextualFiltersIfNeeded(result, normalized);
        return result;
    }

    public boolean isShowtimeSuggestionRequest(String userMessage) {
        String normalized = normalize(userMessage);
        return lexicon.containsAnyGroup(normalized, "showtime-suggestion")
                || isExplicitShowtimeQuestion(normalized)
                || isImplicitShowtimeQuestion(normalized);
    }

    public boolean isQuickGreeting(String userMessage) {
        return lexicon.equalsAnyGroup(normalize(userMessage), "quick-greeting");
    }

    public boolean isQuickGoodbyeOrThanks(String userMessage) {
        return lexicon.equalsAnyGroup(normalize(userMessage), "quick-goodbye-thanks");
    }

    public boolean isCurrentUserIdentityQuestion(String userMessage) {
        String normalized = normalize(userMessage);
        return lexicon.containsAnyGroup(normalized, "identity")
                || (lexicon.contains(normalized, "biết") && lexicon.contains(normalized, "là ai"));
    }

    public boolean isCapabilityQuestion(String userMessage) {
        return lexicon.containsAnyGroup(normalize(userMessage), "capability");
    }

    public boolean isMovieRecommendationQuestion(String userMessage) {
        String normalized = normalize(userMessage);
        return lexicon.contains(normalized, "chua biet chon phim")
                || lexicon.contains(normalized, "nen xem phim")
                || lexicon.contains(normalized, "goi y phim")
                || lexicon.contains(normalized, "tu van phim")
                || (lexicon.contains(normalized, "phim") && lexicon.containsAnyGroup(normalized, "movie-family-mood"))
                || (lexicon.contains(normalized, "dat ve") && lexicon.contains(normalized, "chon phim"));
    }

    public boolean isLoginAcknowledgement(String userMessage) {
        String normalized = normalize(userMessage);
        return (lexicon.contains(normalized, "da dang nhap")
                || lexicon.contains(normalized, "dang nhap roi")
                || lexicon.contains(normalized, "toi dang dang nhap")
                || lexicon.contains(normalized, "minh dang dang nhap"))
                && !isBookingQuestion(normalized)
                && !isLoyaltyQuestion(normalized)
                && !isVoucherQuestion(normalized);
    }

    public String resolveClarificationMessage(String userMessage) {
        return clarificationService.resolveClarificationMessage(userMessage);
    }

    public boolean looksLikeBusinessQuestion(String userMessage) {
        String normalized = normalize(userMessage);
        return lexicon.containsAnyGroup(normalized, "business")
                || containsAny(normalized,
                "tai khoan", "dang ky", "dang nhap", "quen mat khau", "mat khau", "otp",
                "thanh toan", "vnpay", "pay", "payment", "giao dich",
                "don hang", "hoa don", "lich su", "hoan tien", "hoan diem",
                "ma don", "ma giao dich", "ma ve", "ma qr");
    }

    public boolean isOutOfScopeQuestion(String userMessage) {
        String normalized = normalize(userMessage);
        return containsAny(normalized,
                "thoi tiet", "du bao thoi tiet", "mua khong", "nong khong",
                "coin", "crypto", "bitcoin", "chung khoan", "co phieu",
                "bong da", "ket qua tran", "tin tuc", "nau an", "hoc bai");
    }

    public boolean isMoreResultsFollowUp(String userMessage) {
        String normalized = normalize(userMessage);
        return containsAny(normalized,
                "xem them", "them nua", "con nua khong", "con gi khac",
                "khac di", "goi y khac", "doi cai khac", "phim khac", "mon khac");
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
        if ("MOVIES".equals(normalizedPreviousIntent) && isContextualShowtimeFollowUp(normalized)) {
            return "SHOWTIMES";
        }
        if (isCarryableContextIntent(normalizedPreviousIntent) && isContextualFollowUp(normalized)) {
            return normalizedPreviousIntent;
        }
        if (clarificationService.hasReferenceTerm(normalized)
                && clarificationService.isExpiryQuestion(normalized)
                && !lexicon.contains(normalized, "voucher")) {
            return normalizedPreviousIntent;
        }
        if (lexicon.containsAnyGroup(normalized, "context-expiry")) {
            if ("LOYALTY".equals(normalizedPreviousIntent) && !lexicon.contains(normalized, "voucher")) {
                return "LOYALTY";
            }
        }
        return null;
    }

    private boolean isCarryableContextIntent(String previousIntent) {
        return "SNACKS".equals(previousIntent)
                || "SHOWTIMES".equals(previousIntent)
                || "VOUCHERS".equals(previousIntent)
                || "MOVIES".equals(previousIntent);
    }

    private boolean isPriceOrSelectionFollowUp(String normalizedMessage) {
        return hasPriceCue(normalizedMessage)
                || hasDateTerm(normalizedMessage)
                || containsAny(normalizedMessage,
                "cai nao", "mon nao", "loai nao", "cai do", "mon do", "loai do",
                "cai nay", "mon nay", "loai nay", "o tren", "vua roi", "thi sao",
                "vay con", "con cai nao", "con mon nao", "con loai nao",
                "con san pham", "san pham khac", "khong con", "con nua", "con khong",
                "co cai nao", "co loai nao",
                "tat ca", "toan bo", "day du", "liet ke het", "xem het", "xem tat ca");
    }

    private boolean isContextualFollowUp(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return false;
        }
        return isPriceOrSelectionFollowUp(normalizedMessage)
                || hasContextReferenceCue(normalizedMessage)
                || isShortAmbiguousFollowUp(normalizedMessage);
    }

    private boolean hasContextReferenceCue(String normalizedMessage) {
        return clarificationService.hasReferenceTerm(normalizedMessage)
                || containsAny(normalizedMessage,
                "o tren", "vua roi", "luc nay", "ban vua noi", "ket qua tren",
                "cai dau", "cai dau tien", "muc dau", "phim dau", "mon dau",
                "cai thu hai", "muc thu hai", "phim thu hai", "mon thu hai",
                "chi tiet", "noi ro hon", "giai thich them",
                "xem them", "them nua", "con nua khong", "con gi khac",
                "khac di", "goi y khac", "doi cai khac",
                "co lich khong", "co suat khong", "lich chieu", "suat chieu",
                "gia bao nhieu", "bao nhieu tien", "dat duoc khong", "dat ve duoc khong");
    }

    private boolean isContextualShowtimeFollowUp(String normalizedMessage) {
        return containsAny(normalizedMessage,
                "lich", "lich chieu", "suat", "suat chieu", "gio chieu",
                "co lich khong", "co suat khong", "luc may gio", "may gio",
                "khi nao chieu", "hom nay", "toi nay", "ngay mai", "toi mai");
    }

    private boolean isShortAmbiguousFollowUp(String normalizedMessage) {
        int wordCount = normalizedMessage.trim().split("\\s+").length;
        return wordCount <= 7 && containsAny(normalizedMessage,
                "xem", "cho xem", "liet ke", "hien thi", "mo ra",
                "tiep", "them", "nua", "khac", "tat ca", "toan bo",
                "chi tiet", "noi ro", "gia", "lich", "suat", "dat ve");
    }

    public String normalize(String value) {
        return lexicon.normalize(value);
    }

    boolean isImplicitShowtimeQuestion(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return false;
        }
        boolean hasMovieTerm = lexicon.contains(normalizedMessage, "phim");
        boolean hasShowTerm = lexicon.containsAnyGroup(normalizedMessage, "implicit-showtime");
        return hasMovieTerm && hasShowTerm && hasDateTerm(normalizedMessage);
    }

    boolean isExplicitShowtimeQuestion(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return false;
        }
        return lexicon.containsAnyGroup(normalizedMessage, "explicit-showtime");
    }

    boolean isMovieListQuestion(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return false;
        }
        return lexicon.equalsAnyGroup(normalizedMessage, "movie-list-exact")
                || lexicon.containsAnyGroup(normalizedMessage, "movie-list");
    }

    boolean isMovieSearchQuestion(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return false;
        }
        return lexicon.contains(normalizedMessage, "phim")
                && (lexicon.containsAnyGroup(normalizedMessage, "movie-search")
                || lexicon.resolveMovieGenre(normalizedMessage) != null
                || lexicon.containsAnyGroup(normalizedMessage, "movie-light-mood")
                || lexicon.containsAnyGroup(normalizedMessage, "movie-family-mood"));
    }

    private boolean isMovieAvailabilityQuestion(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return false;
        }
        boolean hasMovieSubject = lexicon.contains(normalizedMessage, "phim")
                || hasKnownMovieAlias(normalizedMessage);
        return hasMovieSubject
                && containsAny(normalizedMessage,
                "con chieu", "dang chieu", "co chieu", "hien co chieu", "rap co chieu",
                "con ban ve", "co suat", "co lich", "co phim nay");
    }

    private boolean isSnackQuestion(String normalizedMessage) {
        return lexicon.containsAnyGroup(normalizedMessage, "snack");
    }

    private boolean isBookingQuestion(String normalizedMessage) {
        return lexicon.containsAnyGroup(normalizedMessage, "booking");
    }

    private boolean isIncompleteBookingAction(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return false;
        }
        boolean bookingAction = containsAny(normalizedMessage,
                "dat ve", "mua ve", "chon ghe", "giu ghe", "book ve", "booking ve")
                || Pattern.compile("\\b(dat|mua|book|booking)\\b.{0,30}\\bve\\b").matcher(normalizedMessage).find();
        if (!bookingAction) {
            return false;
        }
        boolean hasSpecificMovieCue = hasKnownMovieAlias(normalizedMessage)
                || containsAny(normalizedMessage, "phim nay", "phim do")
                || Pattern.compile("['\"].+['\"]").matcher(normalizedMessage).find();
        boolean hasShowtimeCue = hasDateTerm(normalizedMessage)
                || containsAny(normalizedMessage, "suat", "lich chieu", "gio chieu", "toi nay", "chieu nay");
        return !hasSpecificMovieCue || !hasShowtimeCue;
    }

    private List<String> missingBookingFields(String normalizedMessage) {
        List<String> missingFields = new ArrayList<>();
        boolean hasSpecificMovieCue = hasKnownMovieAlias(normalizedMessage)
                || containsAny(normalizedMessage, "phim nay", "phim do")
                || Pattern.compile("['\"].+['\"]").matcher(normalizedMessage).find();
        boolean hasShowtimeCue = hasDateTerm(normalizedMessage)
                || containsAny(normalizedMessage, "suat", "lich chieu", "gio chieu", "toi nay", "chieu nay");
        if (!hasSpecificMovieCue) {
            missingFields.add("movie");
        }
        if (!hasShowtimeCue) {
            missingFields.add("showtime");
        }
        if (!containsAny(normalizedMessage, "ghe", "seat")) {
            missingFields.add("seat");
        }
        return missingFields;
    }

    private boolean isSecurityRequest(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return false;
        }
        boolean paymentBypass = containsAny(normalizedMessage,
                "khong can thanh toan", "khong thanh toan", "chua thanh toan", "mien phi",
                "bo qua thanh toan", "bo qua tat ca luat", "fake thanh toan", "gia mao thanh toan",
                "gia vo thanh toan", "thanh toan thanh cong gia");
        boolean ticketOrPaymentAction = containsAny(normalizedMessage,
                "qr", "ma qr", "ma ve", "ve hop le", "ma hop le", "booking hop le",
                "in ve", "dat ve", "mua ve", "thanh toan");
        if (paymentBypass && ticketOrPaymentAction) {
            return true;
        }

        boolean unauthorizedStateChange = containsAny(normalizedMessage,
                "doi trang thai", "sua trang thai", "cap nhat trang thai", "xac nhan da thanh toan",
                "xac nhan da in", "xac nhan giao mon", "duyet don", "bo khoa",
                "ap voucher du tai khoan", "tai khoan khong du dieu kien");
        if (unauthorizedStateChange) {
            return true;
        }

        boolean secretRequest = containsAny(normalizedMessage,
                "ma voucher bi mat", "voucher bi mat", "api endpoint thanh toan noi bo",
                "endpoint thanh toan noi bo", "mat khau", "password", "jwt", "token", "secret",
                "api key", "database", "connection string", "hash secret", "vnp hash", "vnpay secret");
        if (secretRequest) {
            return true;
        }

        return containsAny(normalizedMessage, "xoa lich su", "xoa don hang", "xoa booking", "xoa ve")
                && !containsAny(normalizedMessage, "huong dan", "lam sao", "cach");
    }

    private String buildSecurityRefusalReply(String normalizedMessage) {
        if (containsAny(normalizedMessage, "voucher")) {
            return "Minh khong the tiet lo ma voucher noi bo hoac ap dung voucher khi tai khoan/don hang khong du dieu kien. Voucher chi duoc kiem tra va ap dung theo luat hop le cua he thong.";
        }
        if (containsAny(normalizedMessage, "api", "endpoint", "token", "secret", "password", "database", "connection string")) {
            return "Minh khong the cung cap endpoint noi bo, token, mat khau, khoa bi mat hoac thong tin cau hinh he thong.";
        }
        if (containsAny(normalizedMessage, "xoa")) {
            return "Minh khong the xoa lich su ve, don hang hoac du lieu thanh toan qua chatbot. Cac thao tac thay doi du lieu phai duoc thuc hien trong man hinh nghiep vu co quyen phu hop.";
        }
        return "Minh khong the tao ve, ma QR, in ve, gia lap thanh toan hoac bo qua quy trinh thanh toan. Ve va don hang chi hop le khi duoc he thong xac nhan thanh toan va xu ly dung luong nghiep vu.";
    }

    private String buildBookingClarificationReply() {
        return String.join("\n",
                "Minh chua du thong tin de ho tro dat ve.",
                "Ban vui long cho minh biet ro phim, ngay/gio chieu va so ghe muon dat. Sau khi co du thong tin, he thong moi co the chuyen sang luong chon ghe va thanh toan.");
    }

    private boolean requiresLogin(CinemaBotIntent intent) {
        return intent == CinemaBotIntent.LOYALTY
                || intent == CinemaBotIntent.BOOKING_INFO
                || intent == CinemaBotIntent.VOUCHERS;
    }

    private boolean isDeterministicIntent(CinemaBotIntent intent, CinemaBotService.QueryAnalysis analysis) {
        if (analysis == null) {
            return false;
        }
        return intent != CinemaBotIntent.GENERAL
                || (analysis.filters != null && !analysis.filters.isEmpty())
                || (analysis.keywords != null && !analysis.keywords.isEmpty());
    }

    private double resolveConfidence(Double modelConfidence, double fallback) {
        if (modelConfidence == null || modelConfidence.isNaN() || modelConfidence.isInfinite()) {
            return fallback;
        }
        return Math.max(0.0, Math.min(1.0, modelConfidence));
    }

    private String buildMissingFieldsReply(List<String> missingFields) {
        if (missingFields == null || missingFields.isEmpty()) {
            return "Minh can them thong tin truoc khi co the tra cuu du lieu cho ban.";
        }
        return "Minh can ban bo sung cac thong tin sau: " + String.join(", ", missingFields) + ".";
    }

    private Map<String, String> extractEntities(
            CinemaBotService.QueryAnalysis analysis,
            String normalizedMessage
    ) {
        Map<String, String> entities = new LinkedHashMap<>();
        CinemaBotIntent intent = CinemaBotIntent.from(analysis != null ? analysis.intent : null);

        if (analysis != null && analysis.entities != null) {
            analysis.entities.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                    entities.put(key.trim(), value.trim());
                }
            });
        }

        if (analysis != null && analysis.filters != null) {
            for (String filter : analysis.filters) {
                if (filter == null || filter.isBlank()) {
                    continue;
                }
                int separator = filter.indexOf(':');
                if (separator <= 0 || separator == filter.length() - 1) {
                    continue;
                }
                entities.put(filter.substring(0, separator).trim(), filter.substring(separator + 1).trim());
            }
        }

        if (analysis != null && analysis.keywords != null) {
            List<String> keywords = analysis.keywords.stream()
                    .filter(keyword -> keyword != null && !keyword.isBlank())
                    .map(String::trim)
                    .toList();
            if (!keywords.isEmpty()) {
                entities.putIfAbsent(primaryEntityKey(intent), keywords.get(0));
                entities.put("keywords", String.join(" | ", keywords));
            }
        }

        addMessageEntities(entities, normalizedMessage);
        return entities;
    }

    private String primaryEntityKey(CinemaBotIntent intent) {
        return switch (intent) {
            case MOVIES, MOVIE_DETAIL, SHOWTIMES -> "movie";
            case SNACKS -> "snack";
            case VOUCHERS -> "voucher";
            default -> "query";
        };
    }

    private void addMessageEntities(Map<String, String> entities, String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return;
        }

        Matcher ticketCount = Pattern.compile("\\b(\\d{1,2})\\s*(?:ve|ticket)\\b").matcher(normalizedMessage);
        if (ticketCount.find()) {
            entities.putIfAbsent("ticket_count", ticketCount.group(1));
        }

        Matcher seat = Pattern.compile("\\b(?:ghe|seat)\\s*([a-z]\\d+(?:\\s*[,/-]\\s*[a-z]?\\d+)*)\\b")
                .matcher(normalizedMessage);
        if (seat.find()) {
            entities.putIfAbsent("seat", seat.group(1).replaceAll("\\s+", ""));
        }

        Matcher time = Pattern.compile("\\b(?:luc|gio|suat)\\s*(\\d{1,2})(?:[:h](\\d{2}))?\\b")
                .matcher(normalizedMessage);
        if (time.find()) {
            String minute = time.group(2) != null ? time.group(2) : "00";
            entities.putIfAbsent("time", String.format("%02d:%s", Integer.parseInt(time.group(1)), minute));
        }
    }

    public boolean isBookingCancellationQuestion(String userMessage) {
        return lexicon.containsAnyGroup(normalize(userMessage), "booking-cancellation");
    }

    public boolean isBookingCancellationRefundQuestion(String userMessage) {
        String normalized = normalize(userMessage);
        return isBookingCancellationQuestion(userMessage)
                && lexicon.containsAnyGroup(normalized, "booking-refund");
    }

    public boolean isBookingCancellationConditionQuestion(String userMessage) {
        String normalized = normalize(userMessage);
        return isBookingCancellationQuestion(userMessage)
                && lexicon.containsAnyGroup(normalized, "booking-condition");
    }

    public boolean isBookingCancellationEligibilityQuestion(String userMessage) {
        String normalized = normalize(userMessage);
        return isBookingCancellationQuestion(userMessage)
                && lexicon.containsAnyGroup(normalized, "booking-eligibility");
    }

    private boolean isLoyaltyQuestion(String normalizedMessage) {
        return lexicon.containsAnyGroup(normalizedMessage, "loyalty")
                || isPointExpiryQuestion(normalizedMessage);
    }

    private boolean isVoucherQuestion(String normalizedMessage) {
        return lexicon.containsAnyGroup(normalizedMessage, "voucher");
    }

    private boolean hasDateTerm(String normalizedMessage) {
        return lexicon.containsAnyGroup(normalizedMessage, "date")
                || normalizedMessage.matches(".*\\b\\d{1,2}[/-]\\d{1,2}([/-]\\d{2,4})?\\b.*")
                || normalizedMessage.matches(".*\\b\\d{4}-\\d{1,2}-\\d{1,2}\\b.*");
    }

    private CinemaBotService.QueryAnalysis copyOrCreate(CinemaBotService.QueryAnalysis source) {
        CinemaBotService.QueryAnalysis result = new CinemaBotService.QueryAnalysis();
        result.intent = CinemaBotIntent.from(source != null ? source.intent : null).name();
        result.keywords = source != null && source.keywords != null ? new ArrayList<>(source.keywords) : new ArrayList<>();
        result.filters = source != null && source.filters != null ? new ArrayList<>(source.filters) : new ArrayList<>();
        result.confidence = source != null ? source.confidence : null;
        result.entities = source != null && source.entities != null
                ? new LinkedHashMap<>(source.entities)
                : new LinkedHashMap<>();
        result.missingFields = source != null && source.missingFields != null
                ? new ArrayList<>(source.missingFields)
                : new ArrayList<>();
        result.requiresLogin = source != null ? source.requiresLogin : null;
        result.allowedToQuery = source != null ? source.allowedToQuery : null;
        adaptEntitiesToLegacyAnalysis(result);
        return result;
    }

    private void adaptEntitiesToLegacyAnalysis(CinemaBotService.QueryAnalysis analysis) {
        if (analysis.entities == null || analysis.entities.isEmpty()) {
            return;
        }

        String primaryKeyword = switch (CinemaBotIntent.from(analysis.intent)) {
            case MOVIES, MOVIE_DETAIL, SHOWTIMES -> analysis.entities.get("movie");
            case SNACKS -> analysis.entities.get("snack");
            case VOUCHERS -> analysis.entities.get("voucher");
            default -> analysis.entities.get("query");
        };
        if (primaryKeyword != null && !primaryKeyword.isBlank() && analysis.keywords.isEmpty()) {
            analysis.keywords.add(primaryKeyword);
        }

        Set<String> filterKeys = Set.of(
                "status", "genre", "excludeGenre", "exclude_genre", "mood", "date", "time", "price_min", "price_max",
                "room_type", "category", "movie_id"
        );
        for (Map.Entry<String, String> entry : analysis.entities.entrySet()) {
            if (filterKeys.contains(entry.getKey()) && !hasFilter(analysis, entry.getKey())) {
                analysis.filters.add(entry.getKey() + ":" + entry.getValue());
            }
        }
    }

    private void addMovieStatusFilterIfNeeded(CinemaBotService.QueryAnalysis analysis, String normalizedMessage) {
        if (hasFilter(analysis, "status")) {
            return;
        }
        if (lexicon.containsAnyGroup(normalizedMessage, "now-showing")) {
            analysis.filters.add("status:NOW_SHOWING");
        } else if (lexicon.containsAnyGroup(normalizedMessage, "coming-soon")) {
            analysis.filters.add("status:COMING_SOON");
        }
    }

    private void addMovieGenreFilterIfNeeded(CinemaBotService.QueryAnalysis analysis, String normalizedMessage) {
        if (hasFilter(analysis, "genre")) {
            return;
        }
        String genre = lexicon.resolveMovieGenre(normalizedMessage);
        if (genre == null) {
            return;
        }
        if (isNegatedMovieGenre(normalizedMessage, genre)) {
            if (!hasFilter(analysis, "excludeGenre")) {
                analysis.filters.add("excludeGenre:" + genre);
            }
        } else {
            analysis.filters.add("genre:" + genre);
        }
    }

    private boolean isNegatedMovieGenre(String normalizedMessage, String genre) {
        if (normalizedMessage == null || genre == null) {
            return false;
        }
        String normalizedGenre = normalize(genre);
        return lexicon.contains(normalizedMessage, "khong " + normalizedGenre)
                || lexicon.contains(normalizedMessage, "khong thich " + normalizedGenre)
                || lexicon.contains(normalizedMessage, "khong muon " + normalizedGenre)
                || ("kinh di".equals(normalizedGenre)
                && (lexicon.contains(normalizedMessage, "khong phim ma")
                || lexicon.contains(normalizedMessage, "khong rung ron")));
    }

    private void addMovieMoodFilterIfNeeded(CinemaBotService.QueryAnalysis analysis, String normalizedMessage) {
        if (hasFilter(analysis, "mood")) {
            return;
        }
        if (lexicon.containsAnyGroup(normalizedMessage, "movie-family-mood")) {
            analysis.filters.add("mood:FAMILY");
        } else if (lexicon.containsAnyGroup(normalizedMessage, "movie-light-mood")) {
            analysis.filters.add("mood:LIGHT");
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

    private void addSnackCategoryFilterIfNeeded(CinemaBotService.QueryAnalysis analysis, String normalizedMessage) {
        if (hasFilter(analysis, "category")) {
            return;
        }
        if (containsAny(normalizedMessage, "bap nuoc", "thuc don", "menu")) {
            return;
        }
        if (lexicon.contains(normalizedMessage, "combo")) {
            analysis.filters.add("category:COMBO");
        } else if (containsAny(normalizedMessage,
                "loai nuoc", "nuoc", "nuoc uong", "do uong", "nuoc ngot", "drink", "coca", "coke", "fanta", "sprite", "dasani")) {
            analysis.filters.add("category:DRINK");
        } else if (containsAny(normalizedMessage, "bap rang", "popcorn", "do an", "snack")) {
            analysis.filters.add("category:SNACK");
        }
    }

    private void addContextualFiltersIfNeeded(CinemaBotService.QueryAnalysis analysis, String normalizedMessage) {
        if (analysis == null || analysis.intent == null) {
            return;
        }
        switch (CinemaBotIntent.from(analysis.intent)) {
            case SNACKS -> {
                addPriceFilterIfNeeded(analysis, normalizedMessage, "price_max");
                addSnackCategoryFilterIfNeeded(analysis, normalizedMessage);
            }
            case SHOWTIMES -> {
                addDateFilterIfNeeded(analysis, normalizedMessage);
                addMovieAliasKeywordIfNeeded(analysis, normalizedMessage);
                addMovieGenreFilterIfNeeded(analysis, normalizedMessage);
                addMovieMoodFilterIfNeeded(analysis, normalizedMessage);
                addPriceFilterIfNeeded(analysis, normalizedMessage, "price_max");
            }
            case MOVIES -> {
                addMovieStatusFilterIfNeeded(analysis, normalizedMessage);
                addMovieAliasKeywordIfNeeded(analysis, normalizedMessage);
                addMovieGenreFilterIfNeeded(analysis, normalizedMessage);
                addMovieMoodFilterIfNeeded(analysis, normalizedMessage);
            }
            case VOUCHERS -> addPriceFilterIfNeeded(analysis, normalizedMessage, "price_min");
            default -> {
            }
        }
    }

    private void addPriceFilterIfNeeded(CinemaBotService.QueryAnalysis analysis, String normalizedMessage, String defaultKey) {
        if (hasFilter(analysis, "price_max") || hasFilter(analysis, "price_min")) {
            return;
        }
        Double amount = extractMoneyAmount(normalizedMessage);
        if (amount == null) {
            return;
        }

        if (containsAny(normalizedMessage, "duoi", "nho hon", "khong qua", "toi da", "duoi muc")) {
            analysis.filters.add("price_max:" + amount.longValue());
        } else if (containsAny(normalizedMessage, "tren", "lon hon", "tu", "don tren", "toi thieu")) {
            analysis.filters.add("price_min:" + amount.longValue());
        } else {
            analysis.filters.add(defaultKey + ":" + amount.longValue());
        }
    }

    private Double extractMoneyAmount(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return null;
        }

        Matcher matcher = Pattern.compile("(\\d+(?:[\\.,]\\d+)?)\\s*(k|nghin|ngan|trieu|m|000|vnd|dong)?")
                .matcher(normalizedMessage);
        while (matcher.find()) {
            String unit = matcher.group(2);
            if ((unit == null || unit.isBlank()) && isNonMoneyNumberContext(normalizedMessage, matcher.start(), matcher.end())) {
                continue;
            }

            double value;
            try {
                value = Double.parseDouble(matcher.group(1).replace(',', '.'));
            } catch (NumberFormatException ignored) {
                continue;
            }

            if (unit == null || unit.isBlank()) {
                return value < 1000 ? value * 1000 : value;
            }
            if ("k".equals(unit) || "nghin".equals(unit) || "ngan".equals(unit)) {
                return value * 1000;
            }
            if ("trieu".equals(unit) || "m".equals(unit)) {
                return value * 1_000_000;
            }
            if ("000".equals(unit)) {
                return value * 1000;
            }
            return value;
        }
        return null;
    }

    private boolean isNonMoneyNumberContext(String normalizedMessage, int start, int end) {
        String after = normalizedMessage.substring(end).trim();
        String before = normalizedMessage.substring(0, start).trim();
        if (after.matches("^(ngay|hom|tuan|thang|nam|gio|phut|ve|ghe|nguoi|tuoi|d|dinh dang|suat)\\b.*")) {
            return true;
        }
        if (before.matches(".*\\b(sau|trong|qua|them|chon|mua|dat|nhan|lay|ve|ghe|phong|room)\\s*$")) {
            return true;
        }
        return after.matches("^[a-zA-Z].*") && !after.matches("^(k|m)\\b.*");
    }

    private boolean hasPriceCue(String normalizedMessage) {
        return extractMoneyAmount(normalizedMessage) != null
                || containsAny(normalizedMessage, "duoi", "tren", "nho hon", "lon hon", "toi da", "toi thieu");
    }

    private boolean containsAny(String normalizedMessage, String... phrases) {
        for (String phrase : phrases) {
            if (lexicon.contains(normalizedMessage, phrase)) {
                return true;
            }
        }
        return false;
    }

    private void addMovieAliasKeywordIfNeeded(CinemaBotService.QueryAnalysis analysis, String normalizedMessage) {
        if (analysis == null || normalizedMessage == null || normalizedMessage.isBlank()) {
            return;
        }
        String aliasKeyword = resolveKnownMovieAliasKeyword(normalizedMessage);
        if (aliasKeyword == null) {
            return;
        }
        if (analysis.keywords == null) {
            analysis.keywords = new ArrayList<>();
        }
        boolean alreadyPresent = analysis.keywords.stream()
                .filter(keyword -> keyword != null)
                .map(this::normalize)
                .anyMatch(keyword -> keyword.equals(normalize(aliasKeyword)));
        if (!alreadyPresent) {
            analysis.keywords.add(aliasKeyword);
        }
    }

    private boolean hasKnownMovieAlias(String normalizedMessage) {
        return resolveKnownMovieAliasKeyword(normalizedMessage) != null;
    }

    private String resolveKnownMovieAliasKeyword(String normalizedMessage) {
        if (containsAny(normalizedMessage, "gau truc", "panda", "kungfu panda", "kung fu panda")) {
            return "Kung Fu Panda";
        }
        if (containsAny(normalizedMessage, "cam xuc", "manh ghep cam xuc", "inside out")) {
            return "Inside Out";
        }
        if (containsAny(normalizedMessage, "minion", "ke trom mat trang", "gru")) {
            return "Ke Trom Mat Trang";
        }
        if (containsAny(normalizedMessage, "nguoi nhen", "spider man", "spiderman")) {
            return "Spider-Man";
        }
        if (containsAny(normalizedMessage, "nguoi doi", "batman", "ky si bong dem")) {
            return "Batman";
        }
        return null;
    }

    private String resolveDateFilterValue(String normalizedMessage) {
        if (lexicon.containsAnyGroup(normalizedMessage, "yesterday")) {
            return normalize("hôm qua");
        }
        if (lexicon.containsAnyGroup(normalizedMessage, "today")) {
            return normalize("hôm nay");
        }
        if (lexicon.containsAnyGroup(normalizedMessage, "tomorrow")) {
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

    public record IntentDecision(
            CinemaBotIntent intent,
            double confidence,
            Map<String, String> entities,
            List<String> missingFields,
            boolean requiresLogin,
            boolean allowedToQuery,
            String directReply
    ) {
        public IntentDecision {
            intent = intent != null ? intent : CinemaBotIntent.GENERAL;
            confidence = Math.max(0.0, Math.min(1.0, confidence));
            entities = entities == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(entities));
            missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        }
    }
}
