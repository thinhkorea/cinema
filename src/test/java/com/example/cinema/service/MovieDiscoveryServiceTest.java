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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
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
}
