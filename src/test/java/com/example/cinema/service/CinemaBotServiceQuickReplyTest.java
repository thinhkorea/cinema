package com.example.cinema.service;

import com.example.cinema.domain.Movie;
import com.example.cinema.domain.Room;
import com.example.cinema.domain.Showtime;
import com.example.cinema.repository.BookingRepository;
import com.example.cinema.repository.MovieRepository;
import com.example.cinema.repository.MovieReviewRepository;
import com.example.cinema.repository.PointTransactionRepository;
import com.example.cinema.repository.ShowtimeRepository;
import com.example.cinema.repository.SnackRepository;
import com.example.cinema.repository.UserRepository;
import com.example.cinema.repository.VoucherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.List;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CinemaBotServiceQuickReplyTest {

    @Mock private MovieRepository movieRepository;
    @Mock private ShowtimeRepository showtimeRepository;
    @Mock private SnackRepository snackRepository;
    @Mock private VoucherRepository voucherRepository;
    @Mock private UserRepository userRepository;
    @Mock private MovieReviewRepository movieReviewRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private PointTransactionRepository pointTransactionRepository;
    @Mock private CinemaRetrievalService retrievalService;

    private final CinemaBotLexicon lexicon = new CinemaBotLexicon();
    private final CinemaBotIntentRouter intentRouter = new CinemaBotIntentRouter(
            lexicon,
            new CinemaBotClarificationService(lexicon)
    );

    @Test
    void repliesToVietnameseGreetingWithoutCallingLlm() {
        CinemaBotService service = service();

        String answer = service.askBot("xin chào");

        assertThat(answer).contains("Cinema Bot");
    }

    @Test
    void repliesToVietnameseThanksWithoutCallingLlm() {
        CinemaBotService service = service();

        String answer = service.askBot("cảm ơn");

        assertThat(answer).isNotBlank();
        assertThat(answer.length()).isGreaterThan(20);
    }

    @Test
    void rejectsBlankQuestion() {
        CinemaBotService service = service();

        assertThatThrownBy(() -> service.askBot("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kh");
    }

    @Test
    void asksForClarificationWhenBusinessQuestionFallsBackToGeneral() {
        CinemaBotService service = service();

        String answer = service.askBot("Rạp đang thế nào?");

        assertThat(answer)
                .contains("Mình chưa hiểu rõ")
                .contains("Lịch chiếu hôm nay")
                .contains("Vé/booking");
    }

    @Test
    void repliesToCapabilityQuestionWithoutGenericClarification() {
        CinemaBotService service = service();

        String answer = service.askBot("Rạp hỗ trợ gì vậy?");

        assertThat(answer)
                .contains("hỗ trợ các chức năng")
                .contains("Tra cứu phim")
                .contains("lịch chiếu")
                .contains("điểm thành viên");
    }

    @Test
    void doesNotTreatTomorrowAsShortMovieTitleMai() {
        Movie mai = new Movie();
        mai.setMovieId(1L);
        mai.setTitle("MAI");
        mai.setGenre("Tình cảm");
        mai.setStatus(Movie.MovieStatus.NOW_SHOWING);

        when(movieRepository.findAll()).thenReturn(List.of(mai));
        when(showtimeRepository.findAllWithActiveRoom()).thenReturn(List.of());

        CinemaBotService service = service();

        String answer = service.askBot("Ngày mai có phim tình cảm nào chiếu không?");

        assertThat(answer).contains("phim thể loại tình cảm");
        assertThat(answer).doesNotContain("phim 'MAI'");
    }

    @Test
    void todayShowtimeQuestionDoesNotReturnPastShowtimes() {
        Movie movie = new Movie();
        movie.setMovieId(7L);
        movie.setTitle("Lật Mặt 7");
        movie.setGenre("Gia đình");
        movie.setStatus(Movie.MovieStatus.NOW_SHOWING);

        Room room = new Room();
        room.setRoomName("Room 4");
        room.setRoomType("IMAX");

        Showtime pastShowtime = new Showtime();
        pastShowtime.setShowtimeId(1L);
        pastShowtime.setMovie(movie);
        pastShowtime.setRoom(room);
        pastShowtime.setStartTime(LocalDateTime.now().minusHours(1));
        pastShowtime.setPrice(90000.0);

        when(movieRepository.findAll()).thenReturn(List.of(movie));
        when(showtimeRepository.findAllWithActiveRoom()).thenReturn(List.of(pastShowtime));

        CinemaBotService service = service();

        String answer = service.askBot("Hôm nay có suất chiếu nào không?");

        assertThat(answer).contains("rạp chưa có suất chiếu");
        assertThat(answer).doesNotContain("Lật Mặt 7");
    }

    private CinemaBotService service() {
        return new CinemaBotService(
                new RestTemplateBuilder(),
                "http://localhost:11434/api/chat",
                "cinema-bot",
                movieRepository,
                showtimeRepository,
                snackRepository,
                voucherRepository,
                userRepository,
                movieReviewRepository,
                bookingRepository,
                pointTransactionRepository,
                retrievalService,
                intentRouter
        );
    }
}
