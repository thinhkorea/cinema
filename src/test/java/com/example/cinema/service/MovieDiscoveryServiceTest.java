package com.example.cinema.service;

import com.example.cinema.domain.Movie;
import com.example.cinema.dto.MovieDiscoveryResultDTO;
import com.example.cinema.repository.MovieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieDiscoveryServiceTest {

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private CinemaRetrievalService retrievalService;

    @Mock
    private MovieDiscoveryRerankService rerankService;

    private MovieDiscoveryService service;

    @BeforeEach
    void setUp() {
        service = new MovieDiscoveryService(
                movieRepository,
                retrievalService,
                rerankService,
                false,
                "test-embedding",
                5
        );
    }

    @Test
    void discoverReturnsEmptyForBlankQueryWithoutLoadingMovies() {
        List<MovieDiscoveryResultDTO> results = service.discover("   ", 3, false);

        assertThat(results).isEmpty();
        verifyNoInteractions(movieRepository, retrievalService, rerankService);
    }

    @Test
    void discoverRanksDescriptionAndGenreMatchesFirst() {
        Movie horror = movie(
                1L,
                "A Quiet Place",
                "Kinh di",
                "Sinh vat ngoai hanh tinh san moi bang am thanh",
                Movie.MovieStatus.NOW_SHOWING
        );
        Movie familyDrama = movie(
                2L,
                "Dieu Ba Mong Muon",
                "Gia dinh, tam ly",
                "Nguoi chau ve cham soc ba bi benh va hoc cach gan ket voi gia dinh",
                Movie.MovieStatus.NOW_SHOWING
        );
        Movie endedMatch = movie(
                3L,
                "Ky Uc Cua Ba",
                "Gia dinh",
                "Nguoi chau ve cham soc ba bi benh",
                Movie.MovieStatus.ENDED
        );

        when(movieRepository.findAll()).thenReturn(List.of(horror, familyDrama, endedMatch));
        when(rerankService.rerank(anyString(), anyList()))
                .thenReturn(MovieDiscoveryRerankService.RerankResponse.empty());

        List<MovieDiscoveryResultDTO> results = service.discover("nguoi chau cham soc ba bi benh", 3, false);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getMovieId()).isEqualTo(2L);
        assertThat(results).extracting(MovieDiscoveryResultDTO::getMovieId).doesNotContain(3L);
        assertThat(results.get(0).getMatchSource()).isEqualTo("LOCAL_SCORING");
        assertThat(results.get(0).getScore()).isGreaterThan(0);
    }

    @Test
    void discoverCanIncludeEndedMoviesWhenRequested() {
        Movie endedMatch = movie(
                3L,
                "Ky Uc Cua Ba",
                "Gia dinh",
                "Nguoi chau ve cham soc ba bi benh",
                Movie.MovieStatus.ENDED
        );

        when(movieRepository.findAll()).thenReturn(List.of(endedMatch));
        when(rerankService.rerank(anyString(), anyList()))
                .thenReturn(MovieDiscoveryRerankService.RerankResponse.empty());

        List<MovieDiscoveryResultDTO> results = service.discover("nguoi chau cham soc ba bi benh", 3, true);

        assertThat(results).extracting(MovieDiscoveryResultDTO::getMovieId).containsExactly(3L);
    }

    @Test
    void discoverUsesExistingEmbeddingSearchWhenEmbeddingIsEnabled() {
        service = new MovieDiscoveryService(
                movieRepository,
                retrievalService,
                rerankService,
                true,
                "test-embedding",
                5
        );
        Movie movie = movie(
                4L,
                "Bo Gia",
                "Gia dinh, hai",
                "Nguoi cha don than va nhung mau thuan gia dinh",
                Movie.MovieStatus.NOW_SHOWING
        );

        when(movieRepository.findAll()).thenReturn(List.of(movie));
        when(retrievalService.denseSearchMoviesUsingExistingEmbeddings(anyString(), anyList()))
                .thenReturn(List.of(new CinemaRetrievalService.DenseCandidate<>(movie, 0.8)));
        when(rerankService.rerank(anyString(), anyList()))
                .thenReturn(MovieDiscoveryRerankService.RerankResponse.empty());

        List<MovieDiscoveryResultDTO> results = service.discover("phim ve gia dinh", 1, false);

        assertThat(results).extracting(MovieDiscoveryResultDTO::getMovieId).containsExactly(4L);
        assertThat(results.get(0).getMatchSource()).isEqualTo("EMBEDDING_MODEL");
        verify(retrievalService).denseSearchMoviesUsingExistingEmbeddings(anyString(), anyList());
        verifyNoMoreInteractions(retrievalService);
    }

    @Test
    void discoverLetsRerankChooseSemanticPlotOverGenericTitleToken() {
        Movie joker = movie(
                4L,
                "Joker: Dien Co Doi",
                "Tam ly, Toi pham, Nhac kich",
                "Arthur Fleck song tai thanh pho Gotham va gap mot nguoi phu nu trong can ho ben canh",
                Movie.MovieStatus.NOW_SHOWING
        );
        Movie matBiec = movie(
                22L,
                "Mat Biec",
                "Lang man, Chinh kich",
                "Mat Biec xoay quanh moi tinh don phuong cua Ngan voi Ha Lan. Ngan va Ha Lan lon len tai lang Do Do, "
                        + "cung trai qua tinh yeu tuoi hoc tro trong sang. Khi len thanh pho Hue, Ha Lan bi cuon vao tinh yeu "
                        + "voi Dung, mang thai va sinh con gai la Tra Long. Ngan thuong den cham soc Ha Lan va cham lo cho "
                        + "Tra Long suot nhieu nam.",
                Movie.MovieStatus.COMING_SOON
        );

        when(movieRepository.findAll()).thenReturn(List.of(joker, matBiec));
        when(rerankService.rerank(anyString(), anyList()))
                .thenReturn(new MovieDiscoveryRerankService.RerankResponse(
                        "test-rerank",
                        Map.of(
                                4L, new MovieDiscoveryRerankService.RerankScore(0.10, null),
                                22L, new MovieDiscoveryRerankService.RerankScore(0.95, null)
                        ),
                        false
                ));

        List<MovieDiscoveryResultDTO> results = service.discover(
                "co mot cap doi 1 nam va 1 nu cung duoc xem la thanh mai truc ma lon len tu nho voi nhau "
                        + "nguoi con trai lon hon nguoi con gai sau nay nguoi con trai len thanh pho di hoc "
                        + "nguoi con gai o que mot minh vai nam sau nguoi con gai cung len thanh pho hoc "
                        + "nhung sau do bi nguoi khac du do va co con rieng ben ngoai va sau do bi nguoi ta bo roi "
                        + "luc nay nguoi con trai da nhan nuoi ca nguoi con gai va con cua co ay den lon",
                2,
                false
        );

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getMovieId()).isEqualTo(22L);
        assertThat(results.get(0).getTitle()).isEqualTo("Mat Biec");
        assertThat(results.get(0).getMatchSource()).isEqualTo("RERANK");
    }

    @Test
    void discoverTrustsRerankOrderWhenRawRerankScoresAreSmall() {
        Movie joker = movie(
                4L,
                "Joker: Dien Co Doi",
                "Tam ly, Toi pham, Nhac kich",
                "Arthur song o thanh pho, gap mot nguoi phu nu, song cung me, co nhieu bien co va bi xa hoi bo roi. "
                        + "Cau chuyen co nhieu chi tiet ve thanh pho, nguoi phu nu, me, con va noi co don.",
                Movie.MovieStatus.NOW_SHOWING
        );
        Movie matBiec = movie(
                22L,
                "Mat Biec",
                "Lang man, Chinh kich",
                "Ngan va Ha Lan la doi ban thanh mai truc ma lon len o lang que. Ngan len thanh pho hoc, "
                        + "Ha Lan sau do len thanh pho, bi Dung du do, sinh con gai Tra Long va bi bo roi. "
                        + "Ngan am tham cham soc Ha Lan va Tra Long.",
                Movie.MovieStatus.COMING_SOON
        );

        when(movieRepository.findAll()).thenReturn(List.of(joker, matBiec));
        when(rerankService.rerank(anyString(), anyList()))
                .thenReturn(new MovieDiscoveryRerankService.RerankResponse(
                        "test-rerank",
                        Map.of(
                                4L, new MovieDiscoveryRerankService.RerankScore(0.021, null),
                                22L, new MovieDiscoveryRerankService.RerankScore(0.038, null)
                        ),
                        false
                ));

        List<MovieDiscoveryResultDTO> results = service.discover(
                "co mot cap doi 1 nam va 1 nu cung duoc xem la thanh mai truc ma lon len tu nho voi nhau "
                        + "nguoi con trai len thanh pho di hoc nguoi con gai o que sau do cung len thanh pho "
                        + "bi nguoi khac du do co con rieng va bi bo roi nguoi con trai nhan nuoi cham soc den lon",
                2,
                false
        );

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getMovieId()).isEqualTo(22L);
        assertThat(results.get(0).getTitle()).isEqualTo("Mat Biec");
        assertThat(results.get(0).getRerankScore()).isGreaterThanOrEqualTo(0.038);
        assertThat(results.get(0).getRerankScore()).isLessThan(1.0);
        assertThat(results.get(0).getRawRerankScore()).isEqualTo(0.038);
    }

    @Test
    void discoverDoesNotLetTinyRerankNoiseOverrideLocalPlotMatch() {
        Movie genericDrama = movie(
                1L,
                "MAI",
                "Tam ly, Tinh cam",
                "Mot co gai song giua thanh pho, co mot dua con gai va trai qua nhieu bien co gia dinh.",
                Movie.MovieStatus.NOW_SHOWING
        );
        Movie matBiec = movie(
                22L,
                "Mat Biec",
                "Lang man, Chinh kich",
                "Ngan va Ha Lan la doi ban thanh mai truc ma lon len tu nho voi nhau o lang que. "
                        + "Khi truong thanh, Ngan len thanh pho Hue di hoc, con Ha Lan o que roi sau do cung len thanh pho. "
                        + "Ha Lan bi Dung du do, mang thai va sinh con gai Tra Long nhung bi bo roi. "
                        + "Ngan am tham cham soc Ha Lan va che cho Tra Long suot nhieu nam.",
                Movie.MovieStatus.COMING_SOON
        );

        when(movieRepository.findAll()).thenReturn(List.of(genericDrama, matBiec));
        when(rerankService.rerank(anyString(), anyList()))
                .thenReturn(new MovieDiscoveryRerankService.RerankResponse(
                        "test-rerank",
                        Map.of(
                                1L, new MovieDiscoveryRerankService.RerankScore(0.002, null),
                                22L, new MovieDiscoveryRerankService.RerankScore(0.001, null)
                        ),
                        false
                ));

        List<MovieDiscoveryResultDTO> results = service.discover(
                "co mot cap doi thanh mai truc ma lon len tu nho voi nhau nguoi con trai len thanh pho di hoc "
                        + "nguoi con gai o que sau do len thanh pho bi du do co con rieng va bi bo roi "
                        + "nguoi con trai nhan nuoi cham soc den lon",
                2,
                false
        );

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getMovieId()).isEqualTo(22L);
        assertThat(results.get(0).getRerankScore()).isLessThan(0.02);
        assertThat(results.get(0).getRawRerankScore()).isEqualTo(0.001);
    }

    @Test
    void discoverDoesNotPromoteWeakRerankScoresToFullConfidence() {
        service = new MovieDiscoveryService(
                movieRepository,
                retrievalService,
                rerankService,
                true,
                "test-embedding",
                5
        );
        Movie joker = movie(
                4L,
                "Joker: Dien Co Doi",
                "Tam ly, Toi pham, Nhac kich",
                "Arthur song tai thanh pho Gotham, gap mot nguoi phu nu va phat hien minh tung duoc nhan nuoi. "
                        + "Cau chuyen xoay quanh ao tuong, toi pham va bien co xa hoi.",
                Movie.MovieStatus.NOW_SHOWING
        );
        Movie matBiec = movie(
                22L,
                "Mat Biec",
                "Lang man, Chinh kich",
                "Ngan va Ha Lan la doi ban thanh mai truc ma lon len tu nho voi nhau o lang que. "
                        + "Khi truong thanh, Ngan len thanh pho Hue di hoc, con Ha Lan o que roi sau do cung len thanh pho. "
                        + "Ha Lan bi Dung du do, mang thai va sinh con gai Tra Long nhung bi bo roi. "
                        + "Ngan am tham cham soc Ha Lan va cham lo cho Tra Long den lon.",
                Movie.MovieStatus.COMING_SOON
        );

        when(movieRepository.findAll()).thenReturn(List.of(joker, matBiec));
        when(retrievalService.denseSearchMoviesUsingExistingEmbeddings(anyString(), anyList()))
                .thenReturn(List.of(
                        new CinemaRetrievalService.DenseCandidate<>(joker, 0.735),
                        new CinemaRetrievalService.DenseCandidate<>(matBiec, 0.763)
                ));
        when(rerankService.rerank(anyString(), anyList()))
                .thenReturn(new MovieDiscoveryRerankService.RerankResponse(
                        "test-rerank",
                        Map.of(
                                4L, new MovieDiscoveryRerankService.RerankScore(0.177, null),
                                22L, new MovieDiscoveryRerankService.RerankScore(0.006, null)
                        ),
                        false
                ));

        List<MovieDiscoveryResultDTO> results = service.discover(
                "co mot cap doi 1 nam va 1 nu cung duoc xem la thanh mai truc ma lon len tu nho voi nhau "
                        + "nguoi con trai lon hon nguoi con gai sau nay nguoi con trai len thanh pho di hoc "
                        + "chi con nguoi con gai o que mot minh vai nam sau nguoi con gai cung len thanh pho hoc "
                        + "nhung sau do bi nguoi khac du do va co con rieng ben ngoai va sau do bi nguoi ta bo roi "
                        + "luc nay nguoi con trai da nhan nuoi ca nguoi con gai va con cua co ay den lon",
                2,
                true
        );

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getMovieId()).isEqualTo(22L);
        MovieDiscoveryResultDTO jokerResult = results.stream()
                .filter(result -> result.getMovieId().equals(4L))
                .findFirst()
                .orElseThrow();
        assertThat(jokerResult.getRerankScore()).isEqualTo(0.177);
        assertThat(jokerResult.getRerankScore()).isLessThan(1.0);
    }

    @Test
    void discoverRanksAccentedPlotDescriptionAboveGenericDistractorWithLocalScoring() {
        Movie joker = movie(
                4L,
                "Joker: Dien Co Doi",
                "Tam ly, Toi pham, Nhac kich",
                "Arthur Fleck song tai thanh pho Gotham cung nguoi me Penny. Anh gap mot phu nu hang xom, "
                        + "bi xa hoi bo roi va roi vao nhung bien co toi pham trong thanh pho.",
                Movie.MovieStatus.NOW_SHOWING
        );
        Movie matBiec = movie(
                22L,
                "Mat Biec",
                "Lang man, Chinh kich",
                "Ngan va Ha Lan la doi ban thanh mai truc ma lon len tu nho voi nhau o lang que. "
                        + "Khi truong thanh, Ngan len thanh pho Hue di hoc, con Ha Lan o que roi sau do cung len thanh pho. "
                        + "Ha Lan bi Dung du do, mang thai va sinh con gai Tra Long nhung bi bo roi. "
                        + "Ngan am tham cham soc Ha Lan va che cho Tra Long suot nhieu nam.",
                Movie.MovieStatus.COMING_SOON
        );

        when(movieRepository.findAll()).thenReturn(List.of(joker, matBiec));
        when(rerankService.rerank(anyString(), anyList()))
                .thenReturn(MovieDiscoveryRerankService.RerankResponse.empty());

        List<MovieDiscoveryResultDTO> results = service.discover(
                "Có một cặp đôi thanh mai trúc mã lớn lên từ nhỏ với nhau, "
                        + "người con trai lên thành phố đi học còn người con gái ở quê. "
                        + "Sau đó cô gái lên thành phố, bị người khác dụ dỗ, có con riêng rồi bị bỏ rơi. "
                        + "Người con trai nhận nuôi và chăm sóc hai mẹ con đến lớn.",
                2,
                false
        );

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getMovieId()).isEqualTo(22L);
        assertThat(results.get(0).getTitle()).isEqualTo("Mat Biec");
        assertThat(results.get(0).getMatchSource()).isEqualTo("LOCAL_SCORING");
        assertThat(results).extracting(MovieDiscoveryResultDTO::getScore)
                .allMatch(score -> score < 100.0);
    }

    @Test
    void discoverDoesNotClampLongPlotRankingScoresToOneHundred() {
        List<Movie> movies = seedLikeMovies();
        when(movieRepository.findAll()).thenReturn(movies);
        when(rerankService.rerank(anyString(), anyList()))
                .thenReturn(MovieDiscoveryRerankService.RerankResponse.empty());

        List<MovieDiscoveryResultDTO> results = service.discover(
                "co mot cap doi 1 nam va 1 nu cung duoc xem la thanh mai truc ma lon len tu nho voi nhau "
                        + "nguoi con trai lon hon nguoi con gai sau nay nguoi con trai len thanh pho di hoc "
                        + "nguoi con gai o que mot minh vai nam sau nguoi con gai cung len thanh pho hoc "
                        + "nhung sau do bi nguoi khac du do va co con rieng ben ngoai va sau do bi nguoi ta bo roi "
                        + "luc nay nguoi con trai da nhan nuoi ca nguoi con gai va con cua co ay den lon",
                5,
                false
        );

        assertThat(results).hasSize(5);
        assertThat(results.get(0).getMovieId()).isEqualTo(22L);
        assertThat(results).extracting(MovieDiscoveryResultDTO::getScore)
                .allMatch(score -> score < 100.0);
        assertThat(results.get(0).getScore()).isGreaterThan(results.get(1).getScore());
    }

    @Test
    void discoverRanksShortExactTitleQueryAboveSemanticDistractors() {
        service = new MovieDiscoveryService(
                movieRepository,
                retrievalService,
                rerankService,
                true,
                "test-embedding",
                5
        );
        Movie semanticDistractor = movie(
                100L,
                "Family Wish",
                "Gia dinh, Chinh kich",
                "Cau chuyen cam dong ve mot nguoi cha va cac mau thuan trong gia dinh.",
                Movie.MovieStatus.NOW_SHOWING
        );
        Movie exactTitleMatch = movie(
                101L,
                "Short Name",
                "Tam ly",
                "Mot cau chuyen rieng ve nhan vat chinh va lua chon ca nhan.",
                Movie.MovieStatus.NOW_SHOWING
        );
        List<Movie> movies = List.of(semanticDistractor, exactTitleMatch);

        when(movieRepository.findAll()).thenReturn(movies);
        when(retrievalService.denseSearchMoviesUsingExistingEmbeddings(anyString(), anyList()))
                .thenReturn(List.of(
                        new CinemaRetrievalService.DenseCandidate<>(semanticDistractor, 0.780),
                        new CinemaRetrievalService.DenseCandidate<>(exactTitleMatch, 0.700)
                ));
        when(rerankService.rerank(anyString(), anyList()))
                .thenReturn(MovieDiscoveryRerankService.RerankResponse.empty());

        List<MovieDiscoveryResultDTO> results = service.discover("Short Name", 5, false);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getMovieId()).isEqualTo(101L);
        assertThat(results.get(0).getTitle()).contains("Short Name");
        assertThat(results.get(0).getScore()).isGreaterThan(results.get(1).getScore());
    }

    @Test
    void discoverRanksSeedMovieScenariosCorrectlyWithLocalScoring() {
        List<Movie> movies = seedLikeMovies();
        when(movieRepository.findAll()).thenReturn(movies);
        when(rerankService.rerank(anyString(), anyList()))
                .thenReturn(MovieDiscoveryRerankService.RerankResponse.empty());

        List<SearchExpectation> expectations = List.of(
                new SearchExpectation(
                        "nguoi chau ve cham soc ba bi ung thu de duoc thua ke gia tai",
                        12L,
                        "Gia Tai Cua Ngoai"
                ),
                new SearchExpectation(
                        "sinh vat ngoai hanh tinh san moi bang am thanh",
                        8L,
                        "Vung Dat Cam Lang: Ngay Mot"
                ),
                new SearchExpectation(
                        "cu bung tay cua thanos lam mat nua vu tru",
                        18L,
                        "Avengers: Endgame"
                ),
                new SearchExpectation(
                        "gia dinh ngheo gia mao danh tinh de vao nha giau",
                        16L,
                        "Parasite"
                ),
                new SearchExpectation(
                        "ke trom xam nhap giac mo de cay mot y tuong",
                        14L,
                        "Inception"
                ),
                new SearchExpectation(
                        "nha vat ly tao bom nguyen tu trong lich su",
                        10L,
                        "Oppenheimer"
                ),
                new SearchExpectation(
                        "batman doi dau joker ten toi pham hon loan",
                        13L,
                        "The Dark Knight"
                ),
                new SearchExpectation(
                        "po can tim chien binh rong moi",
                        9L,
                        "Kung Fu Panda 4"
                ),
                new SearchExpectation(
                        "barbie va ken roi barbieland den the gioi thuc",
                        20L,
                        "Barbie"
                ),
                new SearchExpectation(
                        "hai anh em tho sua ong nuoc mario luigi giai cuu cong chua peach",
                        25L,
                        "The Super Mario Bros. Movie"
                ),
                new SearchExpectation(
                        "peter parker bi lo danh tinh nho doctor strange giup do",
                        17L,
                        "Spider-Man: No Way Home"
                ),
                new SearchExpectation(
                        "bo phim ve gia dinh trong do ong bo co mot dua con trai ten quan nhung dua con trai do ra ngoai an choi "
                                + "xong roi co mot dua con gai sau do nguoi cha ay da nuoi chau noi duoi danh nghia la con gai "
                                + "thay vi la chau noi va nguoi con trai kia lam nghe livestream ke ve chuyen doi moi thu trong cuoc song "
                                + "nhung co mot cau nguoi con trai ay noi lam toi nho mai do la ban da co mot tam anh nao ma chup chung voi cha chua",
                        21L,
                        "Bo Gia"
                ),
                new SearchExpectation(
                        "co mot cap doi 1 nam va 1 nu cung duoc xem la thanh mai truc ma lon len tu nho voi nhau "
                                + "nguoi con trai lon hon nguoi con gai sau nay nguoi con trai len thanh pho di hoc "
                                + "nguoi con gai o que mot minh vai nam sau nguoi con gai cung len thanh pho hoc "
                                + "nhung sau do bi nguoi khac du do va co con rieng ben ngoai va sau do bi nguoi ta bo roi "
                                + "luc nay nguoi con trai da nhan nuoi ca nguoi con gai va con cua co ay den lon",
                        22L,
                        "Mat Biec"
                )
        );

        for (SearchExpectation expectation : expectations) {
            List<MovieDiscoveryResultDTO> results = service.discover(expectation.query(), 5, false);

            assertThat(results)
                    .as("Search query should return results: %s", expectation.query())
                    .isNotEmpty();
            assertThat(results.get(0).getMovieId())
                    .as("Top result for query: %s", expectation.query())
                    .isEqualTo(expectation.expectedMovieId());
            assertThat(results.get(0).getTitle()).contains(expectation.expectedTitleFragment());
            assertThat(results.get(0).getMatchSource()).isEqualTo("LOCAL_SCORING");
        }
    }

    private List<Movie> seedLikeMovies() {
        return List.of(
                movie(1L, "MAI", "Tam ly, Tinh cam",
                        "Cau chuyen ve Mai, mot nu nhan vien massage, vo tinh gap Duong, mot anh chang nhac cong dao hoa.",
                        Movie.MovieStatus.NOW_SHOWING),
                movie(2L, "Deadpool & Wolverine", "Hanh dong, Hai, Sieu anh hung",
                        "To chuc TVA keo Deadpool vao mot nhiem vu moi xuyen da vu tru, buoc anh phai hop tac voi mot Wolverine cau kinh.",
                        Movie.MovieStatus.NOW_SHOWING),
                movie(3L, "Inside Out 2", "Hoat hinh, Hai, Gia dinh",
                        "Riley buoc vao tuoi thieu nien. Tru so Cam Xuc bi xao tron boi su xuat hien cua cac cam xuc moi.",
                        Movie.MovieStatus.NOW_SHOWING),
                movie(4L, "Joker: Dien Co Doi", "Tam ly, Toi pham, Nhac kich",
                        "Arthur Fleck bi giam giu tai nha thuong dien Arkham. Tai day, han gap go va nay sinh tinh cam voi Harley Quinn.",
                        Movie.MovieStatus.NOW_SHOWING),
                movie(5L, "Dune: Hanh Tinh Cat - Phan Hai", "Khoa hoc vien tuong, Phieu luu",
                        "Paul Atreides hop nhat voi nguoi Fremen de tra thu nhung ke da huy hoai gia dinh anh.",
                        Movie.MovieStatus.NOW_SHOWING),
                movie(6L, "Godzilla x Kong: De Che Moi", "Hanh dong, Phieu luu",
                        "Kong va Godzilla tai hop de chong lai mot moi de doa khong lo an sau ben trong Trai Dat.",
                        Movie.MovieStatus.NOW_SHOWING),
                movie(7L, "Lat Mat 7: Mot Dieu Uoc", "Gia dinh, Chinh kich",
                        "Cau chuyen cam dong ve ba Hai va 5 nguoi con. Sau mot tai nan, ba Hai can nguoi cham soc.",
                        Movie.MovieStatus.NOW_SHOWING),
                movie(8L, "Vung Dat Cam Lang: Ngay Mot", "Kinh di, Khoa hoc vien tuong",
                        "Lay boi canh ngay dau tien the gioi bi cac sinh vat ngoai hanh tinh san moi bang am thanh xam luoc.",
                        Movie.MovieStatus.NOW_SHOWING),
                movie(9L, "Kung Fu Panda 4", "Hoat hinh, Hanh dong, Hai",
                        "Po duoc chon de tro thanh Lanh dao Tinh than cua Thung lung Binh Yen, nhung cau can tim mot Chien binh Rong moi.",
                        Movie.MovieStatus.NOW_SHOWING),
                movie(10L, "Oppenheimer", "Tieu su, Lich su, Chinh kich",
                        "Cau chuyen ve nha vat ly J. Robert Oppenheimer va vai tro cua ong trong viec phat trien bom nguyen tu.",
                        Movie.MovieStatus.NOW_SHOWING),
                movie(11L, "Ke Trom Mat Trang 4", "Hoat hinh, Hai, Phieu luu",
                        "Gru va gia dinh chao don thanh vien moi, Gru Jr. Nhung ho buoc phai chay tron khi ke thu cu thoat khoi tu.",
                        Movie.MovieStatus.SPECIAL_RELEASE),
                movie(12L, "Gia Tai Cua Ngoai", "Chinh kich, Gia dinh",
                        "Mot chang trai tre bo hoc de ve cham soc nguoi ba dang mac benh ung thu giai doan cuoi, voi hi vong duoc thua huong gia tai.",
                        Movie.MovieStatus.SPECIAL_RELEASE),
                movie(13L, "The Dark Knight", "Hanh dong, Toi pham, Chinh kich",
                        "Batman doi mat voi ke thu lon nhat cua minh, Joker, mot ten toi pham hon loan muon chung minh rang ai cung co the bi tha hoa.",
                        Movie.MovieStatus.SPECIAL_RELEASE),
                movie(14L, "Inception", "Hanh dong, Khoa hoc vien tuong, Giat gan",
                        "Mot ten trom chuyen nghiep danh cap thong tin bang cach xam nhap vao tiem thuc cua nguoi khac, duoc giao nhiem vu cay mot y tuong.",
                        Movie.MovieStatus.SPECIAL_RELEASE),
                movie(15L, "Interstellar", "Khoa hoc vien tuong, Phieu luu, Chinh kich",
                        "Mot nhom phi hanh gia du hanh qua mot ho den vu tru de tim kiem mot hanh tinh moi co the o duoc cho nhan loai.",
                        Movie.MovieStatus.SPECIAL_RELEASE),
                movie(16L, "Parasite", "Chinh kich, Giat gan, Hai den",
                        "Mot gia dinh ngheo xam nhap vao cuoc song cua mot gia dinh giau co bang cach gia mao danh tinh.",
                        Movie.MovieStatus.SPECIAL_RELEASE),
                movie(17L, "Spider-Man: No Way Home", "Hanh dong, Phieu luu, Sieu anh hung",
                        "Danh tinh cua Peter Parker bi lo, cau tim den Doctor Strange de nho giup do. Phep thuat that bai.",
                        Movie.MovieStatus.SPECIAL_RELEASE),
                movie(18L, "Avengers: Endgame", "Hanh dong, Khoa hoc vien tuong, Sieu anh hung",
                        "Sau cu bung tay cua Thanos, cac Avengers con lai tap hop de thuc hien mot ke hoach cuoi cung nham dao nguoc moi thu.",
                        Movie.MovieStatus.SPECIAL_RELEASE),
                movie(19L, "The Batman", "Hanh dong, Toi pham, Chinh kich",
                        "Batman theo duoi Riddler, mot ke giet nguoi hang loat, va kham pha ra nhung bi mat den toi ve tham nhung o Gotham.",
                        Movie.MovieStatus.SPECIAL_RELEASE),
                movie(20L, "Barbie", "Hai, Phieu luu, Gia tuong",
                        "Barbie va Ken roi khoi Barbieland de den the gioi thuc, kham pha niem vui va hiem hoa cua viec song giua con nguoi.",
                        Movie.MovieStatus.SPECIAL_RELEASE),
                movie(21L, "Bo Gia", "Gia dinh, Hai, Chinh kich",
                        "Phim xoay quanh gia dinh ong Ba Sang tai mot xom lao dong. Ba Sang la cha don than, "
                                + "mot minh nuoi con trai Quan va be Bu Tot. Quan la mot YouTuber kiem tien tu cac luot xem tren YouTube, "
                                + "thich an choi va mua do hieu dat tien. Sau do moi nguoi biet Bu Tot la con ruot cua Quan, "
                                + "nhung ong Sang da cuu mang va nuoi chau noi nhu con gai cua minh. "
                                + "Trong mot buoi phat truc tiep tren kenh YouTube, Quan gui loi xin loi den cha.",
                        Movie.MovieStatus.COMING_SOON),
                movie(22L, "Mat Biec", "Lang man, Chinh kich",
                        "Mat Biec la cau chuyen tinh don phuong cua Ngan danh cho Ha Lan. Ngan va Ha Lan la doi ban thanh mai truc ma, "
                                + "lon len tu nho voi nhau o lang Do Do. Khi truong thanh, Ngan len thanh pho Hue di hoc, con Ha Lan o que "
                                + "roi sau do cung len thanh pho. Ha Lan bi Dung du do, mang thai va sinh con gai Tra Long nhung bi bo roi. "
                                + "Ngan am tham cham soc Ha Lan, yeu thuong va che cho Tra Long suot nhieu nam.",
                        Movie.MovieStatus.COMING_SOON),
                movie(23L, "Tiec Trang Mau", "Hai, Chinh kich, Tam ly",
                        "Mot nhom ban than choi mot tro choi nguy hiem: cong khai tat ca tin nhan va cuoc goi tren dien thoai.",
                        Movie.MovieStatus.COMING_SOON),
                movie(24L, "Aquaman and the Lost Kingdom", "Hanh dong, Phieu luu, Gia tuong",
                        "Aquaman phai hop tac voi nguoi em trai Orm de bao ve Atlantis khoi Black Manta.",
                        Movie.MovieStatus.COMING_SOON),
                movie(25L, "The Super Mario Bros. Movie", "Hoat hinh, Phieu luu, Hai",
                        "Hai anh em tho sua ong nuoc Mario va Luigi bi lac vao mot the gioi phep thuat va phai giai cuu Cong chua Peach khoi Bowser.",
                        Movie.MovieStatus.COMING_SOON),
                movie(26L, "Fast X", "Hanh dong, Toi pham, Phieu luu",
                        "Dom Toretto va gia dinh phai doi mat voi Dante Reyes, ke muon tra thu cho cai chet cua cha minh.",
                        Movie.MovieStatus.COMING_SOON),
                movie(27L, "Avatar: The Way of Water", "Khoa hoc vien tuong, Hanh dong, Phieu luu",
                        "Jake Sully va Neytiri cung gia dinh phai roi bo ngoi nha va tim noi an nau tai cac ran san ho.",
                        Movie.MovieStatus.COMING_SOON),
                movie(28L, "Black Panther: Wakanda Forever", "Hanh dong, Phieu luu, Sieu anh hung",
                        "Wakanda thuong tiec Vua T'Challa. Nu hoang Ramonda va Shuri phai chien dau de bao ve quoc gia.",
                        Movie.MovieStatus.COMING_SOON),
                movie(29L, "Tenet", "Hanh dong, Khoa hoc vien tuong, Giat gan",
                        "Mot dac vu bi mat phai ngan chan The chien III thong qua cong nghe nghich dao thoi gian phuc tap.",
                        Movie.MovieStatus.COMING_SOON),
                movie(30L, "Dao, Pho va Piano", "Lich su, Chien tranh, Lang man",
                        "Lay boi canh tran chien 60 ngay dem cuoi nam 1946, phim ke ve moi tinh cua mot anh tu ve va mot co tieu thu Ha Noi.",
                        Movie.MovieStatus.COMING_SOON)
        );
    }

    private Movie movie(Long id, String title, String genre, String description, Movie.MovieStatus status) {
        Movie movie = new Movie();
        movie.setMovieId(id);
        movie.setTitle(title);
        movie.setGenre(genre);
        movie.setDescription(description);
        movie.setDuration(100);
        movie.setStatus(status);
        movie.setAgeRating(Movie.AgeRating.P);
        return movie;
    }

    private record SearchExpectation(String query, Long expectedMovieId, String expectedTitleFragment) {
    }
}
