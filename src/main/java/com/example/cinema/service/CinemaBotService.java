package com.example.cinema.service;

import com.example.cinema.domain.*;
import com.example.cinema.dto.CinemaBotShowtimeSuggestionDTO;
import com.example.cinema.dto.OllamaChatRequestDTO;
import com.example.cinema.dto.OllamaChatResponseDTO;
import com.example.cinema.dto.OllamaMessageDTO;
import com.example.cinema.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CinemaBotService {

    private static final Logger log = LoggerFactory.getLogger(CinemaBotService.class);

    private static final String USER_ROLE = "user";
    private static final String FALLBACK_MESSAGE =
            "Xin lỗi, hệ thống AI hiện chưa sẵn sàng. Vui lòng thử lại sau.";

    // ===== Trọng số Hybrid Scoring =====
    private static final double SEMANTIC_WEIGHT = 0.55;
    private static final double KEYWORD_WEIGHT = 0.25;
    private static final double BUSINESS_WEIGHT = 0.20;
    private static final Duration CHAT_CONTEXT_TTL = Duration.ofMinutes(10);

    private final RestTemplate restTemplate;
    private final String ollamaChatUrl;
    private final String modelName;

    private final MovieRepository movieRepository;
    private final ShowtimeRepository showtimeRepository;
    private final SnackRepository snackRepository;
    private final VoucherRepository voucherRepository;
    private final UserRepository userRepository;
    private final MovieReviewRepository movieReviewRepository;
    private final BookingRepository bookingRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final CinemaRetrievalService retrievalService;
    private final CinemaBotIntentRouter intentRouter;
    private final Map<String, BotConversationContext> conversationContexts = new ConcurrentHashMap<>();

    public CinemaBotService(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${cinema.bot.ollama-url:http://localhost:11434/api/chat}") String ollamaChatUrl,
            @Value("${cinema.bot.model:cinema-bot}") String modelName,
            MovieRepository movieRepository,
            ShowtimeRepository showtimeRepository,
            SnackRepository snackRepository,
            VoucherRepository voucherRepository,
            UserRepository userRepository,
            MovieReviewRepository movieReviewRepository,
            BookingRepository bookingRepository,
            PointTransactionRepository pointTransactionRepository,
            CinemaRetrievalService retrievalService,
            CinemaBotIntentRouter intentRouter
    ) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
        this.ollamaChatUrl = ollamaChatUrl;
        this.modelName = modelName;
        this.movieRepository = movieRepository;
        this.showtimeRepository = showtimeRepository;
        this.snackRepository = snackRepository;
        this.voucherRepository = voucherRepository;
        this.userRepository = userRepository;
        this.movieReviewRepository = movieReviewRepository;
        this.bookingRepository = bookingRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.retrievalService = retrievalService;
        this.intentRouter = intentRouter;
    }

    // =====================================================================
    //  ENTRY POINT - Quy trình Hybrid RAG 5 giai đoạn
    // =====================================================================

    public String askBot(String userMessage) {
        return askBot(userMessage, null);
    }

    public String askBot(String userMessage, String conversationId) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("Câu hỏi không được để trống");
        }

        String cleanedMsg = userMessage.toLowerCase().trim();
        String contextKey = resolveConversationKey(conversationId);
        BotConversationContext previousContext = getValidConversationContext(contextKey);

        // Phản hồi nhanh (0ms) cho lời chào hoặc lời cảm ơn/tạm biệt thông thường
        if (intentRouter.isQuickGreeting(userMessage)) {
            return "Dạ xin chào! Mình là Cinema Bot - trợ lý ảo hỗ trợ thông tin và đặt vé của rạp chiếu phim. Mình có thể giúp gì cho bạn hôm nay?";
        }
        if (intentRouter.isQuickGoodbyeOrThanks(userMessage)) {
            return "Dạ rất vui được hỗ trợ bạn. Chúc bạn có một ngày tốt lành và xem phim vui vẻ nhé!";
        }

        if (intentRouter.isCurrentUserIdentityQuestion(userMessage)) {
            return handleCurrentUserIdentityQuestion();
        }

        String contextualIntent = previousContext != null
                ? intentRouter.resolveContextualIntent(userMessage, previousContext.lastIntent)
                : null;
        if (contextualIntent == null) {
            String clarificationMessage = intentRouter.resolveClarificationMessage(userMessage);
            if (clarificationMessage != null) {
                return clarificationMessage;
            }
        }

        // ===== GIAI ĐOẠN 1: Phân tích & Tiền xử lý (Query Transformation) =====
        QueryAnalysis analysis = contextualIntent != null
                ? contextualAnalysis(contextualIntent)
                : intentRouter.route(userMessage, analyzeQuery(userMessage));
        String intent = analysis != null && analysis.intent != null ? analysis.intent.toUpperCase() : "GENERAL";
        List<String> keywords = analysis != null ? analysis.keywords : null;
        List<String> filters = analysis != null ? analysis.filters : null;
        boolean pointExpiryQuestion = intentRouter.isPointExpiryQuestion(userMessage)
                || ("LOYALTY".equals(contextualIntent) && intentRouter.isExpiryFollowUpQuestion(userMessage));

        log.info("[CinemaBot] intent={}, keywords={}, filters={}", intent, keywords, filters);
        rememberConversationContext(contextKey, intent);

        String ragContext = null;

        // ===== GIAI ĐOẠN 2 + 3: Hybrid Retrieval + Business Logic & Ranking =====
        switch (intent) {
            case "MOVIES":
                String moviesResult = handleMoviesIntent(keywords, filters, userMessage);
                if (moviesResult.startsWith("[DIRECT_REPLY]")) {
                    return moviesResult.substring("[DIRECT_REPLY]".length());
                }
                ragContext = moviesResult;
                break;
            case "MOVIE_DETAIL":
                String movieDetailResult = handleMovieDetailIntent(keywords, cleanedMsg);
                if (movieDetailResult.startsWith("[DIRECT_REPLY]")) {
                    return movieDetailResult.substring("[DIRECT_REPLY]".length());
                }
                ragContext = movieDetailResult;
                break;
            case "SHOWTIMES":
                String showtimesResult = handleShowtimesIntent(keywords, filters, cleanedMsg);
                if (showtimesResult.startsWith("[DIRECT_REPLY]")) {
                    return showtimesResult.substring("[DIRECT_REPLY]".length());
                }
                ragContext = showtimesResult;
                break;
            case "SNACKS":
                String snacksResult = handleSnacksIntent(keywords, filters, userMessage);
                if (snacksResult.startsWith("[DIRECT_REPLY]")) {
                    return snacksResult.substring("[DIRECT_REPLY]".length());
                }
                ragContext = snacksResult;
                break;
            case "LOYALTY":
                String loyaltyResult = handleLoyaltyIntent(userMessage, pointExpiryQuestion);
                if (loyaltyResult.startsWith("[DIRECT_REPLY]")) {
                    return loyaltyResult.substring("[DIRECT_REPLY]".length());
                }
                ragContext = loyaltyResult;
                break;
            case "VOUCHERS":
                String voucherResult = handleVouchersIntent(filters);
                if (voucherResult.startsWith("[DIRECT_REPLY]")) {
                    return voucherResult.substring("[DIRECT_REPLY]".length());
                }
                ragContext = voucherResult;
                break;
            case "BOOKING_INFO":
                String bookingResult = handleBookingInfoIntent(userMessage);
                if (bookingResult.startsWith("[DIRECT_REPLY]")) {
                    return bookingResult.substring("[DIRECT_REPLY]".length());
                }
                ragContext = bookingResult;
                break;
            default:
                // GENERAL - không cần RAG context
                break;
        }

        log.debug("[CinemaBot] ragContextPreview={}", ragContext != null ? ragContext.substring(0, Math.min(ragContext.length(), 500)) : null);

        // ===== GIAI ĐOẠN 4 + 5: Augmentation & Generation =====
        String finalPrompt = buildAugmentedPrompt(ragContext, userMessage, intent);
        return callLlmForGeneration(finalPrompt);
    }

    private QueryAnalysis contextualAnalysis(String intent) {
        QueryAnalysis analysis = new QueryAnalysis();
        analysis.intent = intent;
        analysis.keywords = new ArrayList<>();
        analysis.filters = new ArrayList<>();
        return analysis;
    }

    private String resolveConversationKey(String conversationId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return "user:" + auth.getName();
        }
        if (conversationId != null && !conversationId.isBlank()) {
            return "session:" + conversationId.trim();
        }
        return "session:default";
    }

    private BotConversationContext getValidConversationContext(String contextKey) {
        cleanupExpiredConversationContexts();
        BotConversationContext context = conversationContexts.get(contextKey);
        if (context == null || context.isExpired()) {
            conversationContexts.remove(contextKey);
            return null;
        }
        return context;
    }

    private void rememberConversationContext(String contextKey, String intent) {
        if (contextKey == null || intent == null || !isContextAwareIntent(intent)) {
            return;
        }
        conversationContexts.put(contextKey, new BotConversationContext(intent, LocalDateTime.now()));
    }

    private boolean isContextAwareIntent(String intent) {
        return Set.of("MOVIES", "MOVIE_DETAIL", "SHOWTIMES", "SNACKS", "LOYALTY", "VOUCHERS", "BOOKING_INFO")
                .contains(intent.toUpperCase(Locale.ROOT));
    }

    private void cleanupExpiredConversationContexts() {
        conversationContexts.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private String handleCurrentUserIdentityQuestion() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "Mình chưa xác định được bạn là ai vì request hiện chưa có thông tin đăng nhập hợp lệ. Bạn vui lòng đăng nhập lại rồi hỏi mình nhé.";
        }

        User user = userRepository.findByEmail(auth.getName());
        if (user == null) {
            return "Mình thấy bạn đã đăng nhập, nhưng chưa tìm thấy hồ sơ tài khoản tương ứng trong hệ thống. Bạn vui lòng đăng nhập lại giúp mình nhé.";
        }

        String displayName = user.getFullName() != null && !user.getFullName().isBlank()
                ? user.getFullName()
                : user.getEmail();
        String roleLabel = user.getRole() != null ? user.getRole().name() : "CUSTOMER";
        return String.format("Có ạ. Theo tài khoản đang đăng nhập, bạn là %s, email %s, vai trò %s trong hệ thống.",
                displayName,
                user.getEmail(),
                roleLabel);
    }

    public List<CinemaBotShowtimeSuggestionDTO> suggestShowtimes(String userMessage) {
        if (!isShowtimeSuggestionRequest(userMessage)) {
            return Collections.emptyList();
        }

        String cleanedMsg = userMessage != null ? userMessage.toLowerCase().trim() : "";
        List<Movie> allMovies = movieRepository.findAll();
        Movie matchedMovie = findBestMatchMovie(null, cleanedMsg, allMovies);
        ShowtimeDateRange dateRange = resolveShowtimeDateRange(null, cleanedMsg);

        List<Showtime> candidates = matchedMovie != null
                ? showtimeRepository.findByMovie_MovieIdOrderByStartTimeAsc(matchedMovie.getMovieId())
                : showtimeRepository.findAllWithActiveRoom();

        LocalDateTime now = LocalDateTime.now();
        return candidates.stream()
                .filter(s -> isShowtimeInRequestedWindow(s, dateRange))
                .filter(s -> s.getStartTime() != null && s.getStartTime().isAfter(now))
                .sorted((a, b) -> Double.compare(calculateShowtimeBusinessScore(b), calculateShowtimeBusinessScore(a)))
                .limit(3)
                .map(this::toShowtimeSuggestion)
                .collect(Collectors.toList());
    }

    private boolean isShowtimeSuggestionRequest(String userMessage) {
        return intentRouter.isShowtimeSuggestionRequest(userMessage);
    }

    private CinemaBotShowtimeSuggestionDTO toShowtimeSuggestion(Showtime showtime) {
        Movie movie = showtime.getMovie();
        Long movieId = movie != null ? movie.getMovieId() : null;
        Long showtimeId = showtime.getShowtimeId();
        String bookingPath = movieId != null && showtimeId != null
                ? String.format("/booking/%d/seats/%d", movieId, showtimeId)
                : null;

        return new CinemaBotShowtimeSuggestionDTO(
                showtimeId,
                movieId,
                movie != null ? movie.getTitle() : "N/A",
                movie != null ? movie.getPosterUrl() : null,
                showtime.getRoom() != null ? showtime.getRoom().getRoomName() : null,
                showtime.getRoom() != null ? showtime.getRoom().getRoomType() : null,
                showtime.getStartTime(),
                showtime.getPrice(),
                bookingPath
        );
    }

    // =====================================================================
    //  GIAI ĐOẠN 1: Query Transformation
    // =====================================================================

    /**
     * Phân tích câu hỏi người dùng qua LLM để trích xuất Intent, Keywords và Filters.
     */
    private QueryAnalysis analyzeQuery(String userMessage) {
        String systemPrompt =
            "Bạn là trợ lý phân tích câu hỏi người dùng cho hệ thống rạp chiếu phim. Nhiệm vụ của bạn là trích xuất thông tin dưới dạng JSON.\n" +
            "Hãy xác định chính xác ý định (intent) của câu hỏi từ một trong các giá trị sau:\n" +
            "- MOVIES: Hỏi danh sách phim đang chiếu, phim hay, phim hot tại rạp, tìm phim theo thể loại.\n" +
            "- MOVIE_DETAIL: Hỏi chi tiết, nội dung, mô tả, diễn viên, đánh giá của một phim cụ thể (user đã nêu tên phim rõ ràng).\n" +
            "- SHOWTIMES: Hỏi lịch chiếu, suất chiếu, giờ chiếu, phòng chiếu của phim.\n" +
            "- SNACKS: Hỏi về thực đơn bắp nước, đồ ăn, đồ uống, combo của rạp.\n" +
            "- LOYALTY: Hỏi điểm tích lũy thành viên, điểm thưởng, điểm cá nhân.\n" +
            "- VOUCHERS: Hỏi mã giảm giá, voucher, khuyến mãi, ưu đãi.\n" +
            "- BOOKING_INFO: Hỏi lịch sử đặt vé, vé đã mua, tra cứu booking cá nhân.\n" +
            "- GENERAL: Chào hỏi, tạm biệt, cảm ơn, hoặc các câu hỏi nằm ngoài các chủ đề trên.\n" +
            "\n" +
            "Hãy trích xuất filters nếu người dùng có đề cập đến điều kiện lọc, theo định dạng \"key:value\":\n" +
            "- price_max:<số tiền> (giới hạn giá tối đa)\n" +
            "- price_min:<số tiền> (giới hạn giá tối thiểu)\n" +
            "- genre:<thể loại> (thể loại phim: hành động, kinh dị, tình cảm, hoạt hình, ...)\n" +
            "- room_type:<loại phòng> (2D, 3D, IMAX)\n" +
            "- category:<loại snack> (COMBO, DRINK, SNACK)\n" +
            "\n" +
            "Trả về DUY NHẤT một chuỗi JSON chuẩn có định dạng sau (không viết lời giải thích nào khác):\n" +
            "{\n" +
            "  \"intent\": \"TÊN_INTENT\",\n" +
            "  \"keywords\": [\"các cụm từ quan trọng như tên phim hoặc tên sản phẩm\"],\n" +
            "  \"filters\": [\"key:value nếu có\"]\n" +
            "}\n" +
            "Lưu ý: Trả về chuỗi JSON thô trực tiếp, không sử dụng markdown code block.";

        try {
            OllamaChatRequestDTO request = new OllamaChatRequestDTO(
                    modelName,
                    List.of(
                        new OllamaMessageDTO("system", systemPrompt),
                        new OllamaMessageDTO(USER_ROLE, userMessage)
                    ),
                    false
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            OllamaChatResponseDTO response = restTemplate.postForObject(
                    ollamaChatUrl,
                    new HttpEntity<>(request, headers),
                    OllamaChatResponseDTO.class
            );

            if (response != null && response.content() != null) {
                return parseAnalysis(response.content());
            }
        } catch (Exception e) {
            System.err.println("[CinemaBot] Lỗi gọi LLM phân tích query: " + e.getMessage());
        }

        QueryAnalysis fallback = new QueryAnalysis();
        fallback.intent = "GENERAL";
        return fallback;
    }

    // =====================================================================
    //  GIAI ĐOẠN 2: Hybrid Retrieval (Dense + Sparse)
    // =====================================================================

    // ----- Dense Retrieval: Query DB trực tiếp theo intent -----

    /**
     * [MOVIES] Dense + Sparse Retrieval, sau đó Hard Filter + Hybrid Scoring.
     */
    private String handleMoviesIntent(List<String> keywords, List<String> filters, String userMessage) {
        // Dense Retrieval: Lấy phim đang chiếu
        List<Movie> allMovies = movieRepository.findAll();
        List<CinemaRetrievalService.DenseCandidate<Movie>> denseCandidates = retrievalService.denseSearchMovies(userMessage, allMovies);
        List<Movie> denseResults = denseCandidates.stream()
                .map(CinemaRetrievalService.DenseCandidate::item)
                .collect(Collectors.toList());
        Map<Long, Double> semanticScores = denseCandidates.stream()
                .filter(candidate -> candidate.item().getMovieId() != null)
                .collect(Collectors.toMap(
                        candidate -> candidate.item().getMovieId(),
                        CinemaRetrievalService.DenseCandidate::score,
                        Math::max
                ));
        List<Movie> nowShowingResults = movieRepository.findByStatus(Movie.MovieStatus.NOW_SHOWING);

        // Sparse Retrieval: Tìm phim theo keyword (bao gồm cả phim COMING_SOON, SPECIAL_RELEASE)
        List<Movie> sparseResults = retrievalService.sparseSearchMovies(keywords, allMovies);

        // Merge kết quả (loại trùng)
        Set<Long> seenIds = new HashSet<>();
        List<Movie> mergedMovies = new ArrayList<>();
        for (Movie m : denseResults) {
            if (seenIds.add(m.getMovieId())) mergedMovies.add(m);
        }
        for (Movie m : nowShowingResults) {
            if (seenIds.add(m.getMovieId())) mergedMovies.add(m);
        }
        for (Movie m : sparseResults) {
            if (seenIds.add(m.getMovieId())) mergedMovies.add(m);
        }

        // GIAI ĐOẠN 3: Hard Filtering
        mergedMovies = applyMovieHardFilters(mergedMovies, filters);

        if (mergedMovies.isEmpty()) {
            return "[DIRECT_REPLY]Dạ hiện tại rạp chưa có phim nào phù hợp với yêu cầu của bạn ạ. Bạn có muốn mình hỗ trợ tìm kiếm theo tiêu chí khác không?";
        }

        // GIAI ĐOẠN 3: Hybrid Scoring
        List<ScoredItem<Movie>> scoredMovies = new ArrayList<>();
        for (Movie m : mergedMovies) {
            double semanticScore = semanticScores.getOrDefault(
                    m.getMovieId(),
                    nowShowingResults.contains(m) ? 0.6 : 0.2
            );
            double keywordScore = calculateKeywordScore(m.getTitle(), keywords);
            double businessScore = calculateMovieBusinessScore(m);
            scoredMovies.add(new ScoredItem<>(m, semanticScore, keywordScore, businessScore));
        }

        // Sắp xếp theo tổng điểm giảm dần
        scoredMovies.sort((a, b) -> Double.compare(b.totalScore(), a.totalScore()));

        // Format kết quả
        StringBuilder sb = new StringBuilder("Danh sách phim tại rạp (đã xếp hạng theo mức độ phù hợp):\n");
        for (int i = 0; i < scoredMovies.size(); i++) {
            Movie m = scoredMovies.get(i).item;
            Double avgRating = movieReviewRepository.findAverageRatingByMovieId(m.getMovieId());
            long reviewCount = movieReviewRepository.countByMovie_MovieId(m.getMovieId());
            sb.append(String.format("- %s (Thể loại: %s, Thời lượng: %d phút, Độ tuổi: %s, Trạng thái: %s, Đánh giá: %s/5 từ %d lượt)",
                    m.getTitle(), m.getGenre(), m.getDuration(), m.getAgeRating(),
                    formatMovieStatus(m.getStatus()),
                    avgRating != null ? String.format("%.1f", avgRating) : "Chưa có",
                    reviewCount));
            if (i < scoredMovies.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * [MOVIE_DETAIL] Tra cứu chi tiết 1 phim cụ thể.
     */
    private String handleMovieDetailIntent(List<String> keywords, String cleanedMsg) {
        List<Movie> allMovies = movieRepository.findAll();

        // Sparse search tìm phim khớp keyword
        Movie matchedMovie = findBestMatchMovie(keywords, cleanedMsg, allMovies);

        if (matchedMovie == null) {
            return "[DIRECT_REPLY]Dạ mình không tìm thấy phim nào khớp với yêu cầu của bạn ạ. Bạn có thể cho mình biết tên phim chính xác hơn được không?";
        }

        // Lấy thông tin bổ sung
        Double avgRating = movieReviewRepository.findAverageRatingByMovieId(matchedMovie.getMovieId());
        long reviewCount = movieReviewRepository.countByMovie_MovieId(matchedMovie.getMovieId());

        // Lấy suất chiếu sắp tới
        List<Showtime> futureShowtimes = showtimeRepository.findByMovie_MovieIdOrderByStartTimeAsc(matchedMovie.getMovieId())
                .stream()
                .filter(s -> s.getStartTime() != null && s.getStartTime().isAfter(LocalDateTime.now()))
                .limit(5)
                .collect(Collectors.toList());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Thông tin chi tiết phim '%s':\n", matchedMovie.getTitle()));
        sb.append(String.format("- Thể loại: %s\n", matchedMovie.getGenre()));
        sb.append(String.format("- Thời lượng: %d phút\n", matchedMovie.getDuration()));
        sb.append(String.format("- Độ tuổi: %s\n", matchedMovie.getAgeRating()));
        sb.append(String.format("- Trạng thái: %s\n", formatMovieStatus(matchedMovie.getStatus())));
        if (matchedMovie.getActors() != null && !matchedMovie.getActors().isEmpty()) {
            sb.append(String.format("- Diễn viên: %s\n", matchedMovie.getActors()));
        }
        if (matchedMovie.getDescription() != null && !matchedMovie.getDescription().isEmpty()) {
            sb.append(String.format("- Mô tả: %s\n", matchedMovie.getDescription()));
        }
        sb.append(String.format("- Đánh giá: %s/5 từ %d lượt đánh giá\n",
                avgRating != null ? String.format("%.1f", avgRating) : "Chưa có", reviewCount));

        if (!futureShowtimes.isEmpty()) {
            sb.append("- Các suất chiếu sắp tới:\n");
            for (Showtime s : futureShowtimes) {
                sb.append(String.format("  + Phòng: %s, Giờ chiếu: %s, Giá: %,.0f VNĐ\n",
                        s.getRoom() != null ? s.getRoom().getRoomName() : "N/A",
                        s.getStartTime().format(formatter),
                        s.getPrice() != null ? s.getPrice() : 0.0));
            }
        } else {
            sb.append("- Hiện chưa có suất chiếu sắp tới nào cho phim này.");
        }

        return sb.toString();
    }

    /**
     * [SHOWTIMES] Tra cứu lịch chiếu với Hard Filtering + Scoring.
     */
    private String handleShowtimesIntent(List<String> keywords, List<String> filters, String cleanedMsg) {
        List<Movie> allMovies = movieRepository.findAll();

        // Tìm phim cụ thể qua keyword
        Movie matchedMovie = findBestMatchMovie(keywords, cleanedMsg, allMovies);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        ShowtimeDateRange dateRange = resolveShowtimeDateRange(filters, cleanedMsg);

        if (matchedMovie != null) {
            List<Showtime> showtimes = showtimeRepository.findByMovie_MovieIdOrderByStartTimeAsc(matchedMovie.getMovieId());

            // Hard Filtering: Chỉ suất chiếu tương lai
            List<Showtime> futureShowtimes = showtimes.stream()
                    .filter(s -> isShowtimeInRequestedWindow(s, dateRange))
                    .collect(Collectors.toList());

            // Áp dụng filter từ user (room_type, price)
            futureShowtimes = applyShowtimeFilters(futureShowtimes, filters);

            if (futureShowtimes.isEmpty() && dateRange.explicitDate) {
                return String.format("[DIRECT_REPLY]D\u1ea1 ng\u00e0y %s ch\u01b0a c\u00f3 su\u1ea5t chi\u1ebfu n\u00e0o cho phim '%s'%s \u1ea1.",
                        dateRange.label, matchedMovie.getTitle(),
                        hasActiveFilters(filters) ? " ph\u00f9 h\u1ee3p v\u1edbi \u0111i\u1ec1u ki\u1ec7n l\u1ecdc c\u1ee7a b\u1ea1n" : "");
            }

            if (futureShowtimes.isEmpty()) {
                return String.format("[DIRECT_REPLY]Dạ hiện tại chưa có suất chiếu sắp tới nào cho phim '%s'%s ạ. Bạn có muốn mình tìm phim khác đang có suất chiếu không?",
                        matchedMovie.getTitle(), hasActiveFilters(filters) ? " phù hợp với điều kiện lọc của bạn" : "");
            }

            // Hybrid Scoring cho suất chiếu
            List<ScoredItem<Showtime>> scoredShowtimes = new ArrayList<>();
            for (Showtime s : futureShowtimes) {
                double semanticScore = 1.0; // Đã match phim cụ thể
                double keywordScore = 1.0;
                double businessScore = calculateShowtimeBusinessScore(s);
                scoredShowtimes.add(new ScoredItem<>(s, semanticScore, keywordScore, businessScore));
            }
            scoredShowtimes.sort((a, b) -> Double.compare(b.totalScore(), a.totalScore()));

            StringBuilder sb = new StringBuilder();
            if (dateRange.explicitDate) {
                sb.append(String.format("Danh sách lịch chiếu ngày %s của phim '%s':\n", dateRange.label, matchedMovie.getTitle()));
            } else {
            sb.append(String.format("Danh sách lịch chiếu sắp tới của phim '%s' (khách hàng có thể mua vé trực tuyến ngay, hệ thống cho phép đặt trước giờ chiếu 10 phút):\n",
                    matchedMovie.getTitle()));
            }
            for (ScoredItem<Showtime> scored : scoredShowtimes) {
                Showtime s = scored.item;
                sb.append(String.format("- Phòng chiếu: %s%s, Bắt đầu: %s, Giá: %,.0f VNĐ\n",
                        s.getRoom() != null ? s.getRoom().getRoomName() : "N/A",
                        s.getRoom() != null && s.getRoom().getRoomType() != null ? " (" + s.getRoom().getRoomType() + ")" : "",
                        s.getStartTime().format(formatter),
                        s.getPrice() != null ? s.getPrice() : 0.0));
            }
            return sb.toString();
        } else {
            // Không tìm được phim cụ thể → hiển thị tất cả suất chiếu sắp tới
            List<Showtime> todayShowtimes = showtimeRepository.findAllWithActiveRoom();
            List<Showtime> futureShowtimes = todayShowtimes.stream()
                    .filter(s -> isShowtimeInRequestedWindow(s, dateRange))
                    .collect(Collectors.toList());

            futureShowtimes = applyShowtimeFilters(futureShowtimes, filters);

            if (futureShowtimes.isEmpty() && dateRange.explicitDate) {
                return String.format("[DIRECT_REPLY]D\u1ea1 ng\u00e0y %s r\u1ea1p ch\u01b0a c\u00f3 su\u1ea5t chi\u1ebfu n\u00e0o%s \u1ea1.",
                        dateRange.label, hasActiveFilters(filters) ? " ph\u00f9 h\u1ee3p v\u1edbi \u0111i\u1ec1u ki\u1ec7n l\u1ecdc c\u1ee7a b\u1ea1n" : "");
            }

            if (futureShowtimes.isEmpty()) {
                return "[DIRECT_REPLY]Dạ hiện tại rạp chưa có suất chiếu sắp tới nào ạ. Bạn có muốn xem danh sách phim đang chiếu hoặc sắp chiếu không?";
            }

            // Hybrid Scoring
            List<ScoredItem<Showtime>> scoredShowtimes = new ArrayList<>();
            for (Showtime s : futureShowtimes) {
                double semanticScore = 1.0;
                double keywordScore = s.getMovie() != null ? calculateKeywordScore(s.getMovie().getTitle(), keywords) : 0.0;
                double businessScore = calculateShowtimeBusinessScore(s);
                scoredShowtimes.add(new ScoredItem<>(s, semanticScore, keywordScore, businessScore));
            }
            scoredShowtimes.sort((a, b) -> Double.compare(b.totalScore(), a.totalScore()));

            // Giới hạn 10 suất chiếu
            List<ScoredItem<Showtime>> topShowtimes = scoredShowtimes.stream().limit(10).collect(Collectors.toList());

            StringBuilder sb = new StringBuilder("Danh sách suất chiếu sắp tới tại rạp (đã xếp hạng theo mức độ phù hợp, khách hàng có thể mua vé trực tuyến ngay, hệ thống cho phép đặt trước giờ chiếu 10 phút):\n");
            if (dateRange.explicitDate) {
                sb.setLength(0);
                sb.append(String.format("Danh sách suất chiếu ngày %s tại rạp:\n", dateRange.label));
            }
            for (ScoredItem<Showtime> scored : topShowtimes) {
                Showtime s = scored.item;
                sb.append(String.format("- Phim: %s, Phòng: %s%s, Giờ chiếu: %s, Giá: %,.0f VNĐ\n",
                        s.getMovie() != null ? s.getMovie().getTitle() : "N/A",
                        s.getRoom() != null ? s.getRoom().getRoomName() : "N/A",
                        s.getRoom() != null && s.getRoom().getRoomType() != null ? " (" + s.getRoom().getRoomType() + ")" : "",
                        s.getStartTime().format(formatter),
                        s.getPrice() != null ? s.getPrice() : 0.0));
            }
            return sb.toString();
        }
    }

    /**
     * [SNACKS] Tra cứu bắp nước với Hard Filtering + Hybrid Scoring.
     */
    private String handleSnacksIntent(List<String> keywords, List<String> filters, String userMessage) {
        // Dense Retrieval: Lấy snack đang available
        List<Snack> allSnacks = snackRepository.findAll();
        List<CinemaRetrievalService.DenseCandidate<Snack>> denseCandidates = retrievalService.denseSearchSnacks(userMessage, allSnacks);
        List<Snack> denseResults = denseCandidates.stream()
                .map(CinemaRetrievalService.DenseCandidate::item)
                .collect(Collectors.toList());
        Map<Long, Double> semanticScores = denseCandidates.stream()
                .filter(candidate -> candidate.item().getSnackId() != null)
                .collect(Collectors.toMap(
                        candidate -> candidate.item().getSnackId(),
                        CinemaRetrievalService.DenseCandidate::score,
                        Math::max
                ));
        List<Snack> availableResults = snackRepository.findByAvailableTrue();

        // Sparse Retrieval: Tìm theo keyword trong tất cả snack
        List<Snack> sparseResults = retrievalService.sparseSearchSnacks(keywords, allSnacks);

        // Merge kết quả
        Set<Long> seenIds = new HashSet<>();
        List<Snack> mergedSnacks = new ArrayList<>();
        for (Snack s : denseResults) {
            if (seenIds.add(s.getSnackId())) mergedSnacks.add(s);
        }
        for (Snack s : availableResults) {
            if (seenIds.add(s.getSnackId())) mergedSnacks.add(s);
        }
        for (Snack s : sparseResults) {
            if (seenIds.add(s.getSnackId())) mergedSnacks.add(s);
        }

        // Hard Filtering
        mergedSnacks = applySnackHardFilters(mergedSnacks, filters);

        if (mergedSnacks.isEmpty()) {
            return "[DIRECT_REPLY]Dạ hiện tại rạp chưa có bắp nước hoặc combo nào phù hợp với yêu cầu của bạn ạ. Bạn có muốn xem toàn bộ thực đơn không?";
        }

        // Hybrid Scoring
        List<ScoredItem<Snack>> scoredSnacks = new ArrayList<>();
        for (Snack s : mergedSnacks) {
            double semanticScore = semanticScores.getOrDefault(
                    s.getSnackId(),
                    availableResults.contains(s) ? 0.6 : 0.2
            );
            double keywordScore = calculateKeywordScore(s.getSnackName(), keywords);
            double businessScore = calculateSnackBusinessScore(s);
            scoredSnacks.add(new ScoredItem<>(s, semanticScore, keywordScore, businessScore));
        }
        scoredSnacks.sort((a, b) -> Double.compare(b.totalScore(), a.totalScore()));

        StringBuilder sb = new StringBuilder("Thực đơn bắp nước và combo (đã xếp hạng theo mức độ phù hợp):\n");
        for (int i = 0; i < scoredSnacks.size(); i++) {
            Snack s = scoredSnacks.get(i).item;
            sb.append(String.format("- %s [%s] (Mô tả: %s, Giá: %,.0f VNĐ%s)",
                    s.getSnackName(),
                    s.getCategory() != null ? s.getCategory().name() : "OTHER",
                    s.getDescription() != null ? s.getDescription() : "N/A",
                    s.getPrice(),
                    !Boolean.TRUE.equals(s.getAvailable()) ? " - TẠM HẾT" : ""));
            if (i < scoredSnacks.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * [LOYALTY] Tra cứu điểm thành viên (yêu cầu đăng nhập).
     */
    private String handleLoyaltyIntent(String userMessage, boolean pointExpiryQuestion) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "[DIRECT_REPLY]Bạn cần đăng nhập tài khoản để mình có thể kiểm tra điểm tích lũy thành viên giúp bạn nhé.";
        }

        String email = auth.getName();
        User user = userRepository.findByEmail(email);
        if (user == null || user.getCustomer() == null) {
            return "[DIRECT_REPLY]Không tìm thấy thông tin thành viên của bạn. Vui lòng đăng nhập lại.";
        }

        Customer customer = user.getCustomer();
        if (pointExpiryQuestion) {
            return "[DIRECT_REPLY]" + buildPointExpiryReply(user, customer);
        }
        return String.format("Thông tin thành viên của bạn:\n- Họ tên: %s\n- Số điểm tích lũy hiện có: %d điểm (tương đương %,.0f VNĐ quy đổi).",
                user.getFullName(), customer.getLoyaltyPoints(), customer.getLoyaltyPoints() * 1000.0);
    }

    private String buildPointExpiryReply(User user, Customer customer) {
        int currentPoints = customer.getLoyaltyPoints() != null ? customer.getLoyaltyPoints() : 0;
        if (currentPoints <= 0) {
            return "Hiện tại bạn chưa có điểm thành viên khả dụng trong hệ thống.";
        }

        LocalDateTime now = LocalDateTime.now();
        List<PointTransaction> transactions = pointTransactionRepository
                .findByCustomer_CustomerIdOrderByCreatedAtDesc(customer.getCustomerId());

        List<PointTransaction> positiveActiveTransactions = transactions.stream()
                .filter(tx -> tx.getPoints() != null && tx.getPoints() > 0)
                .filter(tx -> tx.getExpiredAt() == null || tx.getExpiredAt().isAfter(now))
                .collect(Collectors.toList());

        if (positiveActiveTransactions.isEmpty()) {
            return String.format("Bạn hiện có %d điểm thành viên. Hệ thống chưa ghi nhận ngày hết hạn chi tiết cho số điểm này.", currentPoints);
        }

        int noExpiryPoints = positiveActiveTransactions.stream()
                .filter(tx -> tx.getExpiredAt() == null)
                .mapToInt(PointTransaction::getPoints)
                .sum();

        Map<LocalDate, Integer> expiringPointsByDate = positiveActiveTransactions.stream()
                .filter(tx -> tx.getExpiredAt() != null)
                .collect(Collectors.groupingBy(
                        tx -> tx.getExpiredAt().toLocalDate(),
                        TreeMap::new,
                        Collectors.summingInt(PointTransaction::getPoints)
                ));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        StringBuilder reply = new StringBuilder();
        reply.append(String.format("Bạn hiện có %d điểm thành viên", currentPoints));
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            reply.append(String.format(" (%s)", user.getFullName()));
        }
        reply.append(". ");

        if (expiringPointsByDate.isEmpty()) {
            reply.append("Số điểm hiện tại chưa có ngày hết hạn cụ thể trong hệ thống.");
        } else {
            Map.Entry<LocalDate, Integer> nearestExpiry = expiringPointsByDate.entrySet().iterator().next();
            reply.append(String.format("Trong đó, %d điểm gần nhất sẽ hết hạn vào ngày %s.",
                    nearestExpiry.getValue(),
                    nearestExpiry.getKey().format(formatter)));
            if (expiringPointsByDate.size() > 1) {
                reply.append(" Các mốc hết hạn khác: ");
                reply.append(expiringPointsByDate.entrySet().stream()
                        .skip(1)
                        .map(entry -> entry.getValue() + " điểm ngày " + entry.getKey().format(formatter))
                        .collect(Collectors.joining("; ")));
                reply.append(".");
            }
        }

        if (noExpiryPoints > 0) {
            reply.append(String.format(" Ngoài ra, %d điểm không có hạn sử dụng.", noExpiryPoints));
        }
        return reply.toString();
    }

    /**
     * [VOUCHERS] Tra cứu mã giảm giá với Hard Filtering + Hybrid Scoring.
     */
    private String handleVouchersIntent(List<String> filters) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "[DIRECT_REPLY]Bạn cần đăng nhập tài khoản để xem danh sách các mã giảm giá (voucher) khả dụng dành cho thành viên.";
        }

        List<Voucher> vouchers = voucherRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        // Hard Filtering: active, chưa dùng hết, chưa hết hạn
        List<Voucher> activeVouchers = vouchers.stream()
                .filter(v -> Boolean.TRUE.equals(v.getActive()))
                .filter(v -> v.getUsageLimit() == null || v.getUsedCount() < v.getUsageLimit())
                .filter(v -> v.getEndAt() == null || v.getEndAt().isAfter(now))
                .filter(v -> v.getStartAt() == null || v.getStartAt().isBefore(now))
                .collect(Collectors.toList());

        // Áp dụng filter giá tối thiểu nếu có
        Double priceMin = extractFilterDouble(filters, "price_min");
        if (priceMin != null) {
            activeVouchers = activeVouchers.stream()
                    .filter(v -> v.getMinOrder() == null || v.getMinOrder() <= priceMin)
                    .collect(Collectors.toList());
        }

        if (activeVouchers.isEmpty()) {
            return "[DIRECT_REPLY]Dạ hiện tại rạp chưa có chương trình voucher hoặc mã giảm giá nào đang áp dụng ạ. Bạn có muốn mình hỗ trợ thông tin khác không?";
        }

        // Hybrid Scoring: ưu tiên voucher giảm nhiều hơn
        List<ScoredItem<Voucher>> scoredVouchers = new ArrayList<>();
        for (Voucher v : activeVouchers) {
            double semanticScore = 1.0;
            double keywordScore = 0.5; // Mặc định
            double businessScore = calculateVoucherBusinessScore(v);
            scoredVouchers.add(new ScoredItem<>(v, semanticScore, keywordScore, businessScore));
        }
        scoredVouchers.sort((a, b) -> Double.compare(b.totalScore(), a.totalScore()));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        StringBuilder sb = new StringBuilder("Danh sách mã giảm giá đang hoạt động (đã xếp hạng theo mức ưu đãi):\n");
        for (int i = 0; i < scoredVouchers.size(); i++) {
            Voucher v = scoredVouchers.get(i).item;
            sb.append(String.format("- Mã: %s (Tên: %s, Giảm: %s%s, Đơn tối thiểu: %,.0f VNĐ%s)",
                    v.getCode(), v.getName(),
                    v.getType() == Voucher.VoucherType.PERCENT ? v.getValue() + "%" : String.format("%,.0f VNĐ", v.getValue()),
                    v.getMaxDiscount() != null ? String.format(", tối đa %,.0f VNĐ", v.getMaxDiscount()) : "",
                    v.getMinOrder() != null ? v.getMinOrder() : 0.0,
                    v.getEndAt() != null ? ", HSD: " + v.getEndAt().format(formatter) : ""));
            if (i < scoredVouchers.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * [BOOKING_INFO] Tra cứu lịch sử đặt vé (yêu cầu đăng nhập).
     */
    private String handleBookingInfoIntent(String userMessage) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "[DIRECT_REPLY]Bạn cần đăng nhập tài khoản để mình có thể kiểm tra lịch sử đặt vé giúp bạn nhé.";
        }

        String email = auth.getName();
        List<Booking> bookings = bookingRepository.findByCustomer_User_Email(email);

        if (intentRouter.isBookingCancellationQuestion(userMessage)) {
            return "[DIRECT_REPLY]" + buildBookingCancellationGuide(bookings);
        }

        // Hard Filtering: chỉ lấy booking PAID, sắp xếp theo createdAt DESC, tối đa 5
        List<Booking> paidBookings = bookings.stream()
                .filter(b -> b.getStatus() == Booking.Status.PAID)
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(5)
                .collect(Collectors.toList());

        if (paidBookings.isEmpty()) {
            return "[DIRECT_REPLY]Dạ bạn chưa có vé nào được đặt và thanh toán thành công tại rạp ạ. Bạn có muốn xem phim đang chiếu để đặt vé không?";
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        StringBuilder sb = new StringBuilder("Lịch sử 5 vé gần nhất đã thanh toán thành công:\n");
        for (int i = 0; i < paidBookings.size(); i++) {
            Booking b = paidBookings.get(i);
            sb.append(String.format("- Vé #%d: Phim '%s', Ghế: %s, Phòng: %s, Giờ chiếu: %s, Tổng tiền: %,.0f VNĐ, Đặt lúc: %s",
                    i + 1,
                    b.getShowtime() != null && b.getShowtime().getMovie() != null ? b.getShowtime().getMovie().getTitle() : "N/A",
                    b.getSeat() != null ? b.getSeat().getSeatNumber() : "N/A",
                    b.getShowtime() != null && b.getShowtime().getRoom() != null ? b.getShowtime().getRoom().getRoomName() : "N/A",
                    b.getShowtime() != null && b.getShowtime().getStartTime() != null ? b.getShowtime().getStartTime().format(formatter) : "N/A",
                    b.getTotal() != null ? b.getTotal() : 0.0,
                    b.getCreatedAt() != null ? b.getCreatedAt().format(formatter) : "N/A"));
            if (i < paidBookings.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }

    private String buildBookingCancellationGuide(List<Booking> bookings) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        List<Booking> cancellableBookings = bookings.stream()
                .filter(b -> b.getStatus() == Booking.Status.PAID)
                .filter(b -> b.getShowtime() != null && b.getShowtime().getStartTime() != null)
                .filter(b -> now.isBefore(b.getShowtime().getStartTime().minusMinutes(30)))
                .sorted(Comparator.comparing(b -> b.getShowtime().getStartTime()))
                .collect(Collectors.toList());

        StringBuilder reply = new StringBuilder();
        reply.append("Bạn có thể hủy vé theo các bước sau:\n");
        reply.append("1. Vào mục Vé của tôi (/my-bookings).\n");
        reply.append("2. Chọn đơn vé cần hủy.\n");
        reply.append("3. Bấm Hủy vé và xác nhận thao tác.\n\n");
        reply.append("Điều kiện hủy vé trong hệ thống:\n");
        reply.append("- Vé phải thuộc tài khoản đang đăng nhập của bạn.\n");
        reply.append("- Vé phải ở trạng thái đã thanh toán.\n");
        reply.append("- Chỉ được hủy trước giờ chiếu ít nhất 30 phút.\n");
        reply.append("- Tiền vé không hoàn tiền mặt, hệ thống hoàn lại thành điểm; điểm hoàn có hạn dùng 3 tháng.");

        if (cancellableBookings.isEmpty()) {
            reply.append("\n\nHiện mình chưa thấy vé đã thanh toán nào còn đủ điều kiện hủy trong tài khoản của bạn.");
            return reply.toString();
        }

        Map<String, List<Booking>> bookingsByTxnRef = cancellableBookings.stream()
                .filter(b -> b.getTxnRef() != null && !b.getTxnRef().isBlank())
                .collect(Collectors.groupingBy(Booking::getTxnRef, LinkedHashMap::new, Collectors.toList()));

        reply.append("\n\nMột số đơn hiện còn có thể hủy:");
        bookingsByTxnRef.entrySet().stream().limit(3).forEach(entry -> {
            List<Booking> group = entry.getValue();
            Booking first = group.get(0);
            String movieTitle = first.getShowtime() != null && first.getShowtime().getMovie() != null
                    ? first.getShowtime().getMovie().getTitle()
                    : "N/A";
            String startTime = first.getShowtime() != null && first.getShowtime().getStartTime() != null
                    ? first.getShowtime().getStartTime().format(formatter)
                    : "N/A";
            reply.append(String.format("\n- Mã giao dịch %s: %s, giờ chiếu %s, %d ghế.",
                    entry.getKey(),
                    movieTitle,
                    startTime,
                    group.size()));
        });

        return reply.toString();
    }

    // =====================================================================
    //  GIAI ĐOẠN 2: Sparse Retrieval Methods
    // =====================================================================

    /**
     * Tìm kiếm phim theo keyword bằng so khớp chuỗi không dấu + Levenshtein similarity.
     */
    /**
     * Tìm kiếm snack theo keyword bằng so khớp chuỗi không dấu.
     */
    // =====================================================================
    //  GIAI ĐOẠN 3: Hard Filtering
    // =====================================================================

    /**
     * Áp dụng bộ lọc thực tế cho danh sách phim.
     * Loại bỏ: phim ENDED + áp dụng filter từ user (genre, price).
     */
    private List<Movie> applyMovieHardFilters(List<Movie> movies, List<String> filters) {
        List<Movie> filtered = movies.stream()
                .filter(m -> m.getStatus() != Movie.MovieStatus.ENDED)
                .collect(Collectors.toList());

        // Áp dụng filter thể loại
        String genreFilter = extractFilter(filters, "genre");
        if (genreFilter != null) {
            String genreNoAccent = removeAccents(genreFilter.toLowerCase().trim());
            filtered = filtered.stream()
                    .filter(m -> m.getGenre() != null && removeAccents(m.getGenre().toLowerCase()).contains(genreNoAccent))
                    .collect(Collectors.toList());
        }

        String statusFilter = extractFilter(filters, "status");
        if (statusFilter != null) {
            try {
                Movie.MovieStatus status = Movie.MovieStatus.valueOf(statusFilter.toUpperCase(Locale.ROOT));
                filtered = filtered.stream()
                        .filter(m -> m.getStatus() == status)
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException ignored) {
                // Bỏ qua status không hợp lệ.
            }
        }

        return filtered;
    }

    /**
     * Áp dụng bộ lọc thực tế cho danh sách snack.
     * Loại bỏ: snack không available + đã hết hạn + áp dụng filter từ user.
     */
    private List<Snack> applySnackHardFilters(List<Snack> snacks, List<String> filters) {
        LocalDate today = LocalDate.now();
        List<Snack> filtered = snacks.stream()
                .filter(s -> Boolean.TRUE.equals(s.getAvailable()))
                .filter(s -> s.getExpiryDate() == null || s.getExpiryDate().isAfter(today))
                .collect(Collectors.toList());

        // Áp dụng filter giá tối đa
        Double priceMax = extractFilterDouble(filters, "price_max");
        if (priceMax != null) {
            filtered = filtered.stream()
                    .filter(s -> s.getPrice() <= priceMax)
                    .collect(Collectors.toList());
        }

        // Áp dụng filter category
        String categoryFilter = extractFilter(filters, "category");
        if (categoryFilter != null) {
            try {
                Snack.SnackCategory cat = Snack.SnackCategory.valueOf(categoryFilter.toUpperCase());
                filtered = filtered.stream()
                        .filter(s -> s.getCategory() == cat)
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException ignored) {
                // Category không hợp lệ, bỏ qua filter này
            }
        }

        return filtered;
    }

    /**
     * Áp dụng filter cho suất chiếu theo room_type và price.
     */
    private List<Showtime> applyShowtimeFilters(List<Showtime> showtimes, List<String> filters) {
        List<Showtime> filtered = new ArrayList<>(showtimes);

        // Filter room_type
        String roomTypeFilter = extractFilter(filters, "room_type");
        if (roomTypeFilter != null) {
            String rtLower = roomTypeFilter.toLowerCase().trim();
            filtered = filtered.stream()
                    .filter(s -> s.getRoom() != null && s.getRoom().getRoomType() != null
                            && s.getRoom().getRoomType().toLowerCase().contains(rtLower))
                    .collect(Collectors.toList());
        }

        // Filter price_max
        Double priceMax = extractFilterDouble(filters, "price_max");
        if (priceMax != null) {
            filtered = filtered.stream()
                    .filter(s -> s.getPrice() != null && s.getPrice() <= priceMax)
                    .collect(Collectors.toList());
        }

        return filtered;
    }

    private boolean isShowtimeInRequestedWindow(Showtime showtime, ShowtimeDateRange dateRange) {
        if (showtime == null || showtime.getStartTime() == null) {
            return false;
        }
        if (dateRange != null && dateRange.explicitDate) {
            return !showtime.getStartTime().isBefore(dateRange.start)
                    && showtime.getStartTime().isBefore(dateRange.end);
        }
        return showtime.getStartTime().isAfter(LocalDateTime.now());
    }

    private ShowtimeDateRange resolveShowtimeDateRange(List<String> filters, String cleanedMsg) {
        LocalDate requestedDate = parseDateValue(extractFilter(filters, "date"));
        if (requestedDate == null) {
            requestedDate = parseDateFromMessage(cleanedMsg);
        }
        return requestedDate == null ? ShowtimeDateRange.upcoming() : ShowtimeDateRange.of(requestedDate);
    }

    private LocalDate parseDateFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        String normalized = normalizeForDateParsing(message);
        LocalDate today = LocalDate.now();

        if (containsNormalizedDate(normalized, "hôm qua") || containsNormalizedDate(normalized, "tối qua")
                || containsNormalizedDate(normalized, "sáng qua") || containsNormalizedDate(normalized, "trưa qua")
                || containsNormalizedDate(normalized, "chiều qua") || containsNormalizedDate(normalized, "đêm qua")) {
            return today.minusDays(1);
        }
        if (containsNormalizedDate(normalized, "hôm nay") || containsNormalizedDate(normalized, "tối nay")
                || containsNormalizedDate(normalized, "sáng nay") || containsNormalizedDate(normalized, "trưa nay")
                || containsNormalizedDate(normalized, "chiều nay")) {
            return today;
        }
        if (containsNormalizedDate(normalized, "ngày mai") || containsNormalizedDate(normalized, "tối mai")
                || containsNormalizedDate(normalized, "sáng mai") || containsNormalizedDate(normalized, "trưa mai")
                || containsNormalizedDate(normalized, "chiều mai")) {
            return today.plusDays(1);
        }

        LocalDate isoDate = parseIsoDateInText(normalized);
        if (isoDate != null) {
            return isoDate;
        }
        return parseSlashDateInText(normalized);
    }

    private LocalDate parseIsoDateInText(String text) {
        Matcher matcher = Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b").matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return LocalDate.of(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private LocalDate parseSlashDateInText(String text) {
        Matcher matcher = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b").matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            int day = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int year = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : LocalDate.now().getYear();
            if (year < 100) {
                year += 2000;
            }
            return LocalDate.of(year, month, day);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private LocalDate parseDateValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = normalizeForDateParsing(value);
        if (equalsNormalizedDate(normalized, "hôm qua")) {
            return LocalDate.now().minusDays(1);
        }
        if (equalsNormalizedDate(normalized, "hôm nay") || equalsNormalizedDate(normalized, "tối nay")
                || equalsNormalizedDate(normalized, "sáng nay") || equalsNormalizedDate(normalized, "trưa nay")
                || equalsNormalizedDate(normalized, "chiều nay")) {
            return LocalDate.now();
        }
        if (equalsNormalizedDate(normalized, "ngày mai")) {
            return LocalDate.now().plusDays(1);
        }

        try {
            return LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            return parseDateFromMessage(normalized);
        }
    }

    private String normalizeForDateParsing(String value) {
        String normalized = java.text.Normalizer.normalize(value.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD);
        return Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(normalized).replaceAll("")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .trim();
    }

    private boolean containsNormalizedDate(String normalizedText, String phrase) {
        return normalizedText != null && normalizedText.contains(normalizeForDateParsing(phrase));
    }

    private boolean equalsNormalizedDate(String normalizedText, String phrase) {
        return normalizedText != null && normalizedText.equals(normalizeForDateParsing(phrase));
    }

    // =====================================================================
    //  GIAI ĐOẠN 3: Hybrid Scoring
    // =====================================================================

    /**
     * Tính Keyword Score: mức độ khớp keyword với tên entity.
     */
    private double calculateKeywordScore(String entityName, List<String> keywords) {
        if (keywords == null || keywords.isEmpty() || entityName == null) return 0.0;

        String nameNoAccent = removeAccents(entityName.toLowerCase().trim());
        double maxScore = 0.0;

        for (String kw : keywords) {
            String kwNoAccent = removeAccents(kw.toLowerCase().trim());
            if (kwNoAccent.isEmpty()) continue;

            // Exact contains → score 1.0
            if (nameNoAccent.contains(kwNoAccent)) {
                maxScore = Math.max(maxScore, 1.0);
            } else {
                // Similarity score
                double similarity = getSimilarity(nameNoAccent, kwNoAccent);
                maxScore = Math.max(maxScore, similarity);
            }
        }
        return maxScore;
    }

    /**
     * Business Score cho Movie:
     * - Đánh giá trung bình (0-5 → normalize 0-1): weight 50%
     * - Số lượng review (normalize): weight 30%
     * - Trạng thái NOW_SHOWING boost: weight 20%
     */
    private double calculateMovieBusinessScore(Movie m) {
        double ratingScore = 0.0;
        double reviewCountScore = 0.0;
        double statusBoost = 0.0;

        // Rating score
        Double avgRating = movieReviewRepository.findAverageRatingByMovieId(m.getMovieId());
        if (avgRating != null) {
            ratingScore = avgRating / 5.0; // Normalize 0-1
        }

        // Review count score (cap at 50 reviews)
        long reviewCount = movieReviewRepository.countByMovie_MovieId(m.getMovieId());
        reviewCountScore = Math.min(reviewCount / 50.0, 1.0);

        // Status boost
        if (m.getStatus() == Movie.MovieStatus.NOW_SHOWING) {
            statusBoost = 1.0;
        } else if (m.getStatus() == Movie.MovieStatus.SPECIAL_RELEASE) {
            statusBoost = 0.8;
        } else if (m.getStatus() == Movie.MovieStatus.COMING_SOON) {
            statusBoost = 0.5;
        }

        return ratingScore * 0.50 + reviewCountScore * 0.30 + statusBoost * 0.20;
    }

    /**
     * Business Score cho Snack:
     * - Category COMBO boost (margin cao): weight 40%
     * - Giá phù hợp (normalize): weight 30%
     * - Tồn kho đầy đủ: weight 30%
     */
    private double calculateSnackBusinessScore(Snack s) {
        double categoryBoost = 0.0;
        double priceScore = 0.0;
        double stockScore = 0.0;

        // COMBO được ưu tiên (margin cao)
        if (s.getCategory() == Snack.SnackCategory.COMBO) {
            categoryBoost = 1.0;
        } else if (s.getCategory() == Snack.SnackCategory.DRINK) {
            categoryBoost = 0.6;
        } else {
            categoryBoost = 0.4;
        }

        // Price score (ưu tiên range phổ biến 30k-100k)
        if (s.getPrice() != null) {
            if (s.getPrice() >= 30000 && s.getPrice() <= 100000) {
                priceScore = 0.8;
            } else if (s.getPrice() < 30000) {
                priceScore = 0.5;
            } else {
                priceScore = 0.6; // Combo lớn
            }
        }

        // Stock score
        if (Boolean.TRUE.equals(s.getWarehouseTrackable())) {
            stockScore = s.getWarehouseStock() > s.getWarehouseReorderLevel() ? 1.0 : 0.3;
        } else {
            stockScore = 1.0; // Không theo dõi kho → luôn sẵn sàng
        }

        return categoryBoost * 0.40 + priceScore * 0.30 + stockScore * 0.30;
    }

    /**
     * Business Score cho Showtime:
     * - Giờ chiếu gần nhất được ưu tiên: weight 40%
     * - Phòng IMAX/3D boost: weight 30%
     * - Giá hợp lý: weight 30%
     */
    private double calculateShowtimeBusinessScore(Showtime s) {
        double timeScore = 0.0;
        double roomBoost = 0.0;
        double priceScore = 0.0;

        // Time score: suất gần nhất trong 24h → điểm cao nhất
        if (s.getStartTime() != null) {
            long hoursUntil = Duration.between(LocalDateTime.now(), s.getStartTime()).toHours();
            if (hoursUntil <= 3) timeScore = 1.0;
            else if (hoursUntil <= 12) timeScore = 0.8;
            else if (hoursUntil <= 24) timeScore = 0.6;
            else if (hoursUntil <= 72) timeScore = 0.4;
            else timeScore = 0.2;
        }

        // Room type boost
        if (s.getRoom() != null && s.getRoom().getRoomType() != null) {
            String roomType = s.getRoom().getRoomType().toUpperCase();
            if (roomType.contains("IMAX")) roomBoost = 1.0;
            else if (roomType.contains("3D")) roomBoost = 0.7;
            else roomBoost = 0.4;
        } else {
            roomBoost = 0.4;
        }

        // Price score
        if (s.getPrice() != null) {
            if (s.getPrice() >= 50000 && s.getPrice() <= 120000) priceScore = 0.8;
            else priceScore = 0.5;
        }

        return timeScore * 0.40 + roomBoost * 0.30 + priceScore * 0.30;
    }

    /**
     * Business Score cho Voucher:
     * - Giá trị giảm giá cao → điểm cao: weight 50%
     * - Đơn tối thiểu thấp → dễ sử dụng: weight 30%
     * - Còn nhiều lượt sử dụng: weight 20%
     */
    private double calculateVoucherBusinessScore(Voucher v) {
        double discountScore = 0.0;
        double accessibilityScore = 0.0;
        double availabilityScore = 0.0;

        // Discount value
        if (v.getType() == Voucher.VoucherType.PERCENT) {
            discountScore = Math.min(v.getValue() / 50.0, 1.0); // 50% = max score
        } else {
            discountScore = Math.min(v.getValue() / 100000.0, 1.0); // 100k = max score
        }

        // Accessibility (đơn tối thiểu thấp = tốt)
        if (v.getMinOrder() == null || v.getMinOrder() <= 0) {
            accessibilityScore = 1.0;
        } else if (v.getMinOrder() <= 100000) {
            accessibilityScore = 0.8;
        } else if (v.getMinOrder() <= 200000) {
            accessibilityScore = 0.5;
        } else {
            accessibilityScore = 0.3;
        }

        // Availability
        if (v.getUsageLimit() == null) {
            availabilityScore = 1.0;
        } else {
            int remaining = v.getUsageLimit() - v.getUsedCount();
            availabilityScore = Math.min((double) remaining / v.getUsageLimit(), 1.0);
        }

        return discountScore * 0.50 + accessibilityScore * 0.30 + availabilityScore * 0.20;
    }

    // =====================================================================
    //  GIAI ĐOẠN 4: Augmentation (Structured Prompt Template)
    // =====================================================================

    /**
     * Xây dựng prompt có cấu trúc chặt chẽ cho LLM.
     */
    private String buildAugmentedPrompt(String ragContext, String userMessage, String intent) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("=== VAI TRÒ ===\n");
        prompt.append("Bạn là Cinema Bot - trợ lý tư vấn thông minh của rạp chiếu phim. ");
        prompt.append("Bạn giao tiếp bằng tiếng Việt, thân thiện, lịch sự và chuyên nghiệp.\n\n");

        if (ragContext != null) {
            prompt.append("=== DỮ LIỆU THỰC TẾ TỪ HỆ THỐNG ===\n");
            prompt.append(ragContext);
            prompt.append("\n\n");
        }

        prompt.append("=== THÔNG TIN BỔ SUNG ===\n");
        prompt.append(String.format("- Ngày giờ hiện tại: %s\n", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isLoggedIn = auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());
        prompt.append(String.format("- Trạng thái đăng nhập: %s\n", isLoggedIn ? "Đã đăng nhập" : "Chưa đăng nhập"));
        if (isLoggedIn) {
            User currentUser = userRepository.findByEmail(auth.getName());
            if (currentUser != null) {
                prompt.append(String.format("- Tài khoản hiện tại: %s\n",
                        currentUser.getFullName() != null && !currentUser.getFullName().isBlank()
                                ? currentUser.getFullName()
                                : currentUser.getEmail()));
                prompt.append(String.format("- Email tài khoản: %s\n", currentUser.getEmail()));
                prompt.append(String.format("- Vai trò tài khoản: %s\n",
                        currentUser.getRole() != null ? currentUser.getRole().name() : "CUSTOMER"));
            }
        }
        prompt.append(String.format("- Intent phân tích: %s\n\n", intent));

        prompt.append("=== QUY TẮC TRẢ LỜI ===\n");
        prompt.append("1. CHỈ sử dụng dữ liệu thực tế được cung cấp ở trên (nếu có). Tuyệt đối KHÔNG bịa thêm thông tin.\n");
        prompt.append("2. Hiển thị kết quả theo thứ tự ưu tiên (dữ liệu đã được xếp hạng sẵn theo thuật toán Hybrid Scoring).\n");
        prompt.append("3. Trả lời ngắn gọn, tự nhiên, dễ hiểu.\n");
        prompt.append("4. Nếu có sản phẩm/dịch vụ phù hợp, hãy gợi ý hành động tiếp theo cho người dùng (ví dụ: đặt vé, mua combo).\n");
        prompt.append("5. Không hướng dẫn người dùng đi tìm kiếm hoặc kiểm tra ở nơi khác - bạn là nguồn thông tin duy nhất.\n");
        prompt.append("6. Nếu không có dữ liệu thực tế, hãy trả lời dựa trên kiến thức chung về rạp chiếu phim nhưng nói rõ đây là thông tin chung.\n\n");

        prompt.append("=== CÂU HỎI NGƯỜI DÙNG ===\n");
        prompt.append(userMessage);

        return prompt.toString();
    }

    // =====================================================================
    //  GIAI ĐOẠN 5: Generation (Gọi LLM)
    // =====================================================================

    /**
     * Gọi Ollama LLM để tạo câu trả lời cuối cùng.
     */
    private String callLlmForGeneration(String finalPrompt) {
        try {
            OllamaChatRequestDTO request = new OllamaChatRequestDTO(
                    modelName,
                    List.of(new OllamaMessageDTO(USER_ROLE, finalPrompt)),
                    false
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            OllamaChatResponseDTO response = restTemplate.postForObject(
                    ollamaChatUrl,
                    new HttpEntity<>(request, headers),
                    OllamaChatResponseDTO.class
            );

            if (response == null || response.content() == null || response.content().trim().isEmpty()) {
                return FALLBACK_MESSAGE;
            }

            return response.content().trim();
        } catch (ResourceAccessException ex) {
            return FALLBACK_MESSAGE;
        } catch (RestClientResponseException ex) {
            return FALLBACK_MESSAGE;
        } catch (RestClientException ex) {
            return FALLBACK_MESSAGE;
        }
    }

    // =====================================================================
    //  HELPER METHODS
    // =====================================================================

    /**
     * Tìm phim khớp nhất với keyword hoặc câu hỏi gốc.
     */
    private Movie findBestMatchMovie(List<String> keywords, String cleanedMsg, List<Movie> allMovies) {
        Movie matchedMovie = null;

        // Bước 1: Tìm qua keyword
        if (keywords != null && !keywords.isEmpty()) {
            double bestScore = 0.0;
            for (String kw : keywords) {
                String kwNoAccent = removeAccents(kw.toLowerCase().trim());
                if (kwNoAccent.isEmpty()) continue;
                for (Movie m : allMovies) {
                    String titleNoAccent = removeAccents(m.getTitle().toLowerCase().trim());
                    if (titleNoAccent.contains(kwNoAccent)) {
                        double score = (double) kwNoAccent.length() / titleNoAccent.length();
                        if (score > bestScore) {
                            bestScore = score;
                            matchedMovie = m;
                        }
                    } else {
                        double similarity = getSimilarity(titleNoAccent, kwNoAccent);
                        if (similarity >= 0.70 && similarity > bestScore) {
                            bestScore = similarity;
                            matchedMovie = m;
                        }
                    }
                }
            }
        }

        // Bước 2: Fallback - tìm trong câu hỏi gốc
        if (matchedMovie == null && cleanedMsg != null) {
            String cleanedMsgNoAccent = removeAccents(cleanedMsg);
            double bestScore = 0.0;
            for (Movie m : allMovies) {
                String titleNoAccent = removeAccents(m.getTitle().toLowerCase().trim());
                if (titleNoAccent.length() >= 3 && cleanedMsgNoAccent.contains(titleNoAccent)) {
                    double score = (double) titleNoAccent.length() / cleanedMsgNoAccent.length();
                    if (score > bestScore) {
                        bestScore = score;
                        matchedMovie = m;
                    }
                }
            }
        }

        return matchedMovie;
    }

    /**
     * Chuyển đổi và làm sạch dữ liệu JSON trích xuất từ Ollama.
     */
    private QueryAnalysis parseAnalysis(String responseText) {
        if (responseText == null) return null;
        String cleanJson = responseText.trim();
        
        // Loại bỏ các khối code block markdown nếu có
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.substring(7);
        } else if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.substring(3);
        }
        if (cleanJson.endsWith("```")) {
            cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
        }
        cleanJson = cleanJson.trim();

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(cleanJson, QueryAnalysis.class);
        } catch (Exception e) {
            // Ghi nhận lỗi và fallback về GENERAL
            System.err.println("[CinemaBot] Lỗi parse JSON từ LLM: " + e.getMessage());
            QueryAnalysis fallback = new QueryAnalysis();
            fallback.intent = "GENERAL";
            return fallback;
        }
    }

    /**
     * Trích xuất giá trị filter theo key từ danh sách filters.
     */
    private String extractFilter(List<String> filters, String key) {
        if (filters == null || filters.isEmpty()) return null;
        String prefix = key + ":";
        for (String f : filters) {
            if (f != null && f.startsWith(prefix)) {
                return f.substring(prefix.length()).trim();
            }
        }
        return null;
    }

    /**
     * Trích xuất giá trị filter dạng Double.
     */
    private Double extractFilterDouble(List<String> filters, String key) {
        String value = extractFilter(filters, key);
        if (value == null) return null;
        try {
            return Double.parseDouble(value.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Kiểm tra xem có filter nào đang hoạt động không.
     */
    private boolean hasActiveFilters(List<String> filters) {
        return filters != null && !filters.isEmpty() && filters.stream().anyMatch(f -> f != null && f.contains(":"));
    }

    /**
     * Format trạng thái phim sang tiếng Việt.
     */
    private String formatMovieStatus(Movie.MovieStatus status) {
        if (status == null) return "N/A";
        switch (status) {
            case NOW_SHOWING: return "Đang chiếu";
            case COMING_SOON: return "Sắp chiếu";
            case SPECIAL_RELEASE: return "Suất chiếu đặc biệt";
            case ENDED: return "Đã kết thúc";
            default: return status.name();
        }
    }

    /**
     * Loại bỏ dấu tiếng Việt để so sánh chuỗi không dấu.
     */
    private String removeAccents(String src) {
        if (src == null) return null;
        String nfdNormalizedString = java.text.Normalizer.normalize(src, java.text.Normalizer.Form.NFD);
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfdNormalizedString).replaceAll("")
                .replace('đ', 'd')
                .replace('Đ', 'D');
    }

    /**
     * Tính khoảng cách Levenshtein giữa hai chuỗi.
     */
    private int getLevenshteinDistance(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return Integer.MAX_VALUE;
        }
        int len1 = s1.length();
        int len2 = s2.length();

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= len2; j++) {
                char c2 = s2.charAt(j - 1);
                if (c1 == c2) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }
        return dp[len1][len2];
    }

    /**
     * Tính độ tương đồng từ 0.0 đến 1.0 giữa 2 chuỗi.
     */
    private double getSimilarity(String s1, String s2) {
        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) return 1.0;
        return 1.0 - ((double) getLevenshteinDistance(s1, s2) / maxLength);
    }

    // =====================================================================
    //  INNER CLASSES
    // =====================================================================

    /**
     * Lớp DTO lưu trữ thông tin trích xuất câu hỏi từ Ollama (Giai đoạn 1).
     */
    private static class ShowtimeDateRange {
        final boolean explicitDate;
        final LocalDateTime start;
        final LocalDateTime end;
        final String label;

        private ShowtimeDateRange(boolean explicitDate, LocalDateTime start, LocalDateTime end, String label) {
            this.explicitDate = explicitDate;
            this.start = start;
            this.end = end;
            this.label = label;
        }

        static ShowtimeDateRange upcoming() {
            return new ShowtimeDateRange(false, null, null, null);
        }

        static ShowtimeDateRange of(LocalDate date) {
            return new ShowtimeDateRange(
                    true,
                    date.atStartOfDay(),
                    date.plusDays(1).atStartOfDay(),
                    date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            );
        }
    }

    public static class QueryAnalysis {
        public String intent;
        public List<String> keywords;
        public List<String> filters;

        public String getIntent() { return intent; }
        public void setIntent(String intent) { this.intent = intent; }
        public List<String> getKeywords() { return keywords; }
        public void setKeywords(List<String> keywords) { this.keywords = keywords; }
        public List<String> getFilters() { return filters; }
        public void setFilters(List<String> filters) { this.filters = filters; }
    }

    private static class BotConversationContext {
        final String lastIntent;
        final LocalDateTime updatedAt;

        BotConversationContext(String lastIntent, LocalDateTime updatedAt) {
            this.lastIntent = lastIntent;
            this.updatedAt = updatedAt;
        }

        boolean isExpired() {
            return updatedAt == null || updatedAt.plus(CHAT_CONTEXT_TTL).isBefore(LocalDateTime.now());
        }
    }

    /**
     * Lớp chứa entity kèm điểm Hybrid Scoring (Giai đoạn 3).
     * Trọng số: Semantic 55% + Keyword 25% + Business 20%
     */
    private static class ScoredItem<T> {
        final T item;
        final double semanticScore;
        final double keywordScore;
        final double businessScore;

        ScoredItem(T item, double semanticScore, double keywordScore, double businessScore) {
            this.item = item;
            this.semanticScore = semanticScore;
            this.keywordScore = keywordScore;
            this.businessScore = businessScore;
        }

        double totalScore() {
            return semanticScore * SEMANTIC_WEIGHT + keywordScore * KEYWORD_WEIGHT + businessScore * BUSINESS_WEIGHT;
        }
    }
}
