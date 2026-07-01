package com.example.cinema.service;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
