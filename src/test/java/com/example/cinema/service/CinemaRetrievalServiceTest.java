package com.example.cinema.service;

import com.example.cinema.domain.Movie;
import com.example.cinema.domain.Snack;
import com.example.cinema.dto.CinemaBotEmbeddingRebuildResultDTO;
import com.example.cinema.dto.CinemaBotEmbeddingStatusDTO;
import com.example.cinema.repository.MovieRepository;
import com.example.cinema.repository.SnackRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CinemaRetrievalServiceTest {

    @Mock
    private CinemaEmbeddingService embeddingService;

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private SnackRepository snackRepository;

    private final CinemaSearchDocumentBuilder documentBuilder = new CinemaSearchDocumentBuilder();

    @Test
    void sparseSearchMoviesRanksKeywordMatches() {
        CinemaRetrievalService service = new CinemaRetrievalService(
                embeddingService,
                documentBuilder,
                movieRepository,
                snackRepository
        );
        Movie romance = movie(1L, "Một Ngày Mưa", "Tình cảm", "Câu chuyện nhẹ nhàng cảm động");
        Movie action = movie(2L, "Bão Lửa", "Hành động", "Nhiều pha rượt đuổi");

        List<Movie> results = service.sparseSearchMovies(List.of("tình cảm nhẹ nhàng"), List.of(action, romance));

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getMovieId()).isEqualTo(1L);
    }

    @Test
    void getEmbeddingStatusCountsValidCachedVectors() {
        CinemaRetrievalService service = new CinemaRetrievalService(
                embeddingService,
                documentBuilder,
                movieRepository,
                snackRepository
        );
        Movie embeddedMovie = movie(1L, "Mai", "Tình cảm", "Mô tả");
        embeddedMovie.setSearchEmbedding("movie-ok");
        Movie missingMovie = movie(2L, "Bão Lửa", "Hành động", "Mô tả");
        missingMovie.setSearchEmbedding("movie-missing");
        Snack embeddedSnack = snack(1L, "Combo", "Bắp nước");
        embeddedSnack.setSearchEmbedding("snack-ok");

        when(movieRepository.findAll()).thenReturn(List.of(embeddedMovie, missingMovie));
        when(snackRepository.findAll()).thenReturn(List.of(embeddedSnack));
        when(embeddingService.readEmbedding("movie-ok")).thenReturn(List.of(0.1, 0.2));
        when(embeddingService.readEmbedding("movie-missing")).thenReturn(List.of());
        when(embeddingService.readEmbedding("snack-ok")).thenReturn(List.of(0.3, 0.4));
        when(embeddingService.isAvailable()).thenReturn(true);

        CinemaBotEmbeddingStatusDTO status = service.getEmbeddingStatus();

        assertThat(status.embeddingProviderAvailable()).isTrue();
        assertThat(status.totalMovies()).isEqualTo(2);
        assertThat(status.embeddedMovies()).isEqualTo(1);
        assertThat(status.missingMovieEmbeddings()).isEqualTo(1);
        assertThat(status.totalSnacks()).isEqualTo(1);
        assertThat(status.embeddedSnacks()).isEqualTo(1);
    }

    @Test
    void rebuildEmbeddingsCreatesMissingVectors() {
        CinemaRetrievalService service = new CinemaRetrievalService(
                embeddingService,
                documentBuilder,
                movieRepository,
                snackRepository
        );
        Movie movie = movie(1L, "Mai", "Tình cảm", "Mô tả");
        Snack snack = snack(1L, "Combo", "Bắp nước");

        when(movieRepository.findAll()).thenReturn(List.of(movie));
        when(snackRepository.findAll()).thenReturn(List.of(snack));
        when(embeddingService.createEmbedding(anyString())).thenReturn(List.of(0.1, 0.2, 0.3));
        when(embeddingService.writeEmbedding(any())).thenReturn("{\"version\":3,\"values\":[0.1,0.2,0.3]}");

        CinemaBotEmbeddingRebuildResultDTO result = service.rebuildEmbeddings(false);

        assertThat(result.updatedMovies()).isEqualTo(1);
        assertThat(result.updatedSnacks()).isEqualTo(1);
        assertThat(result.failedMovies()).isZero();
        assertThat(result.failedSnacks()).isZero();

        ArgumentCaptor<Movie> movieCaptor = ArgumentCaptor.forClass(Movie.class);
        ArgumentCaptor<Snack> snackCaptor = ArgumentCaptor.forClass(Snack.class);
        verify(movieRepository).save(movieCaptor.capture());
        verify(snackRepository).save(snackCaptor.capture());
        assertThat(movieCaptor.getValue().getSearchEmbedding()).contains("\"version\":3");
        assertThat(snackCaptor.getValue().getSearchEmbedding()).contains("\"version\":3");
    }

    private Movie movie(Long id, String title, String genre, String description) {
        Movie movie = new Movie();
        movie.setMovieId(id);
        movie.setTitle(title);
        movie.setGenre(genre);
        movie.setDescription(description);
        movie.setDuration(100);
        movie.setStatus(Movie.MovieStatus.NOW_SHOWING);
        return movie;
    }

    private Snack snack(Long id, String name, String description) {
        Snack snack = new Snack();
        snack.setSnackId(id);
        snack.setSnackName(name);
        snack.setDescription(description);
        snack.setCategory(Snack.SnackCategory.COMBO);
        snack.setPrice(50000.0);
        snack.setAvailable(true);
        snack.setWarehouseStock(10.0);
        return snack;
    }
}
