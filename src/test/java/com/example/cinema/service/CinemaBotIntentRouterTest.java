package com.example.cinema.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class CinemaBotIntentRouterTest {

    private final CinemaBotLexicon lexicon = new CinemaBotLexicon();
    private final CinemaBotIntentRouter router = new CinemaBotIntentRouter(
            lexicon,
            new CinemaBotClarificationService(lexicon)
    );

    @Test
    void routesTodayMovieQuestionToShowtimesWithDateFilter() {
        CinemaBotService.QueryAnalysis result = router.route(
                "Hôm nay có chiếu phim nào?",
                analysis("GENERAL")
        );

        assertThat(result.intent).isEqualTo("SHOWTIMES");
        assertThat(result.filters).contains("date:" + router.normalize("hôm nay"));
    }

    @Test
    void routesTodayGenericMovieQuestionToShowtimesWithDateFilter() {
        CinemaBotService.QueryAnalysis result = router.route(
                "Hôm nay có phim gì?",
                analysis("GENERAL")
        );

        assertThat(result.intent).isEqualTo("SHOWTIMES");
        assertThat(result.filters).contains("date:" + router.normalize("hôm nay"));
    }

    @Test
    void routesTonightShowingQuestionToShowtimesWithDateFilter() {
        CinemaBotService.QueryAnalysis result = router.route(
                "Tối nay chiếu gì?",
                analysis("GENERAL")
        );

        assertThat(result.intent).isEqualTo("SHOWTIMES");
        assertThat(result.filters).contains("date:" + router.normalize("hôm nay"));
    }

    @Test
    void routesTomorrowRomanceShowtimeQuestionWithGenreFilter() {
        CinemaBotService.QueryAnalysis result = router.route(
                "Ngày mai có phim tình cảm nào chiếu không?",
                analysis("GENERAL")
        );

        assertThat(result.intent).isEqualTo("SHOWTIMES");
        assertThat(result.filters).contains("date:" + router.normalize("ngày mai"), "genre:tình cảm");
    }

    @Test
    void extractsSnackPriceAndComboFilter() {
        CinemaBotService.QueryAnalysis result = router.route(
                "Rạp có combo nào dưới 80 nghìn không?",
                analysis("GENERAL")
        );

        assertThat(result.intent).isEqualTo("SNACKS");
        assertThat(result.filters).contains("category:COMBO", "price_max:80000");
    }

    @Test
    void extractsVoucherMinimumOrderFilter() {
        CinemaBotService.QueryAnalysis result = router.route(
                "Tôi có voucher nào dùng được cho đơn trên 100 nghìn không?",
                analysis("GENERAL")
        );

        assertThat(result.intent).isEqualTo("VOUCHERS");
        assertThat(result.filters).contains("price_min:100000");
    }

    @Test
    void detectsLoginAcknowledgementWithoutBookingIntent() {
        assertThat(router.isLoginAcknowledgement("Tôi đã đăng nhập rồi mà")).isTrue();
        assertThat(router.route("Tôi đã đăng nhập rồi mà", analysis("GENERAL")).intent).isEqualTo("GENERAL");
    }

    @Test
    void routesMovieRecommendationRequestToMovies() {
        CinemaBotService.QueryAnalysis result = router.route(
                "Tôi muốn đặt vé nhưng chưa biết chọn phim nào",
                analysis("GENERAL")
        );

        assertThat(result.intent).isEqualTo("MOVIES");
        assertThat(result.filters).contains("status:NOW_SHOWING");
    }

    @Test
    void routesLightMoodMovieQuestionWithMoodFilter() {
        CinemaBotService.QueryAnalysis result = router.route(
                "có thể loại phim nhẹ nhàng nào không?",
                analysis("GENERAL")
        );

        assertThat(result.intent).isEqualTo("MOVIES");
        assertThat(result.filters).contains("mood:LIGHT");
    }

    @Test
    void doesNotTreatNegatedHorrorAsHorrorGenre() {
        CinemaBotService.QueryAnalysis result = router.route(
                "Toi muon xem phim de chiu khong kinh di khong cang thang",
                analysis("GENERAL")
        );

        assertThat(result.intent).isEqualTo("MOVIES");
        assertThat(result.filters).contains("mood:LIGHT");
        assertThat(result.filters).noneMatch(filter -> filter != null && filter.startsWith("genre:"));
    }

    @Test
    void routesFamilyMovieQuestionWithFamilyMoodFilter() {
        CinemaBotService.QueryAnalysis result = router.route(
                "Co phim nao hop de di voi ba me cuoi tuan khong?",
                analysis("GENERAL")
        );

        assertThat(result.intent).isEqualTo("MOVIES");
        assertThat(result.filters).contains("mood:FAMILY");
    }

    @Test
    void routesYesterdayShowtimeQuestionToShowtimesWithDateFilter() {
        CinemaBotService.QueryAnalysis result = router.route(
                "Hôm qua hệ thống có suất chiếu nào?",
                analysis("GENERAL")
        );

        assertThat(result.intent).isEqualTo("SHOWTIMES");
        assertThat(result.filters).contains("date:" + router.normalize("hôm qua"));
    }

    @Test
    void routesNowShowingQuestionToMoviesWithStatusFilter() {
        CinemaBotService.QueryAnalysis result = router.route(
                "Phim đang chiếu",
                analysis("GENERAL")
        );

        assertThat(result.intent).isEqualTo("MOVIES");
        assertThat(result.filters).contains("status:NOW_SHOWING");
    }

    @Test
    void routesComingSoonQuestionToMoviesWithStatusFilter() {
        CinemaBotService.QueryAnalysis result = router.route(
                "Danh sách phim sắp chiếu",
                analysis("GENERAL")
        );

        assertThat(result.intent).isEqualTo("MOVIES");
        assertThat(result.filters).contains("status:COMING_SOON");
    }

    @Test
    void routesGenreNowShowingQuestionToMoviesWithFilters() {
        CinemaBotService.QueryAnalysis result = router.route(
                "Cho tôi biết rạp phim hiện có những bộ phim thể loại tình cảm nào đang được chiếu?",
                analysis("GENERAL")
        );

        assertThat(result.intent).isEqualTo("MOVIES");
        assertThat(result.filters).contains("genre:tình cảm", "status:NOW_SHOWING");
    }

    @Test
    void routesGenericGenreQuestionToMoviesWithoutShowtimeFilter() {
        CinemaBotService.QueryAnalysis result = router.route(
                "Có phim tình cảm nào hay không?",
                analysis("GENERAL")
        );

        assertThat(result.intent).isEqualTo("MOVIES");
        assertThat(result.filters).contains("genre:tình cảm");
        assertThat(result.filters).doesNotContain("status:NOW_SHOWING");
    }

    @Test
    void mapsRomanceGenreAliasesToTinhCam() {
        assertThat(router.route("Có phim tình cả nào đang chiếu?", analysis("GENERAL")).filters)
                .contains("genre:tình cảm", "status:NOW_SHOWING");
        assertThat(router.route("Rạp có phim lãng mạn nào không?", analysis("GENERAL")).filters)
                .contains("genre:tình cảm");
        assertThat(router.route("Tôi muốn xem phim tình yêu", analysis("GENERAL")).filters)
                .contains("genre:tình cảm");
    }

    @Test
    void routesCommonBusinessQuestions() {
        assertThat(router.route("Có combo bắp nước nào?", analysis("GENERAL")).intent).isEqualTo("SNACKS");
        assertThat(router.route("Vé của tôi đâu?", analysis("GENERAL")).intent).isEqualTo("BOOKING_INFO");
        assertThat(router.route("Làm sao để có thể hủy vé khi đã đặt rồi?", analysis("GENERAL")).intent).isEqualTo("BOOKING_INFO");
        assertThat(router.route("Điểm của tôi còn bao nhiêu?", analysis("GENERAL")).intent).isEqualTo("LOYALTY");
        assertThat(router.route("Có mã giảm giá không?", analysis("GENERAL")).intent).isEqualTo("VOUCHERS");
    }

    @Test
    void detectsBookingCancellationQuestion() {
        assertThat(router.isBookingCancellationQuestion("Làm sao để có thể hủy vé khi đã đặt rồi?")).isTrue();
        assertThat(router.isBookingCancellationQuestion("Tôi muốn huỷ booking")).isTrue();
    }

    @Test
    void detectsBookingCancellationSubQuestions() {
        assertThat(router.isBookingCancellationRefundQuestion("Hủy vé có được hoàn tiền khong?")).isTrue();
        assertThat(router.isBookingCancellationConditionQuestion("Điều kiện để hủy vé là gì?")).isTrue();
        assertThat(router.isBookingCancellationEligibilityQuestion("Tôi còn vé nào hủy được không?")).isTrue();
    }

    @Test
    void routesPointExpiryQuestionToLoyalty() {
        CinemaBotService.QueryAnalysis result = router.route(
                "Điểm đó sử dụng đến khi nào?",
                analysis("GENERAL")
        );

        assertThat(result.intent).isEqualTo("LOYALTY");
        assertThat(router.isPointExpiryQuestion("Điểm thành viên hết hạn khi nào?")).isTrue();
    }

    @Test
    void asksForClarificationWhenVoucherReferenceHasNoCode() {
        String message = router.resolveClarificationMessage("Hạn sử dụng cho voucher đó");

        assertThat(message).contains("mã voucher").contains("điểm thành viên");
    }

    @Test
    void resolvesExpiryFollowUpFromPreviousLoyaltyContext() {
        String intent = router.resolveContextualIntent("Hạn sử dụng của nó là khi nào?", "LOYALTY");

        assertThat(intent).isEqualTo("LOYALTY");
    }

    @Test
    void detectsCurrentUserIdentityQuestion() {
        assertThat(router.isCurrentUserIdentityQuestion("Biết anh Khánh là ai không?")).isTrue();
        assertThat(router.isCurrentUserIdentityQuestion("Tôi là ai?")).isTrue();
    }

    @Test
    void detectsShowtimeSuggestionRequests() {
        assertThat(router.isShowtimeSuggestionRequest("Tối nay có phim nào chiếu?")).isTrue();
        assertThat(router.isShowtimeSuggestionRequest("Có suất chiếu nào phù hợp không?")).isTrue();
    }

    private CinemaBotService.QueryAnalysis analysis(String intent) {
        CinemaBotService.QueryAnalysis analysis = new CinemaBotService.QueryAnalysis();
        analysis.intent = intent;
        analysis.keywords = new ArrayList<>();
        analysis.filters = new ArrayList<>();
        return analysis;
    }
}
