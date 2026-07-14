package com.example.cinema.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Locale;
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
            addMovieMoodFilterIfNeeded(result, normalized);
            return result;
        }

        if (isExplicitShowtimeQuestion(normalized) || isImplicitShowtimeQuestion(normalized)) {
            result.intent = "SHOWTIMES";
            addDateFilterIfNeeded(result, normalized);
            addMovieGenreFilterIfNeeded(result, normalized);
            addMovieMoodFilterIfNeeded(result, normalized);
            addPriceFilterIfNeeded(result, normalized, "price_max");
            return result;
        }

        if (isMovieListQuestion(normalized) || isMovieSearchQuestion(normalized)) {
            result.intent = "MOVIES";
            addMovieStatusFilterIfNeeded(result, normalized);
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
        return lexicon.containsAnyGroup(normalize(userMessage), "business");
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
        if (lexicon.containsAnyGroup(normalized, "context-expiry")) {
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

    private boolean isSnackQuestion(String normalizedMessage) {
        return lexicon.containsAnyGroup(normalizedMessage, "snack");
    }

    private boolean isBookingQuestion(String normalizedMessage) {
        return lexicon.containsAnyGroup(normalizedMessage, "booking");
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
        result.intent = source != null && source.intent != null ? source.intent : "GENERAL";
        result.keywords = source != null && source.keywords != null ? new ArrayList<>(source.keywords) : new ArrayList<>();
        result.filters = source != null && source.filters != null ? new ArrayList<>(source.filters) : new ArrayList<>();
        return result;
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
        if (genre != null && !isNegatedMovieGenre(normalizedMessage, genre)) {
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
        if (lexicon.contains(normalizedMessage, "combo")) {
            analysis.filters.add("category:COMBO");
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
            double value;
            try {
                value = Double.parseDouble(matcher.group(1).replace(',', '.'));
            } catch (NumberFormatException ignored) {
                continue;
            }

            String unit = matcher.group(2);
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

    private boolean containsAny(String normalizedMessage, String... phrases) {
        for (String phrase : phrases) {
            if (lexicon.contains(normalizedMessage, phrase)) {
                return true;
            }
        }
        return false;
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
}
