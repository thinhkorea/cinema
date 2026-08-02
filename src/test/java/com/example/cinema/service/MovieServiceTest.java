package com.example.cinema.service;

import com.example.cinema.domain.Movie;
import com.example.cinema.repository.MovieRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private CinemaQdrantService qdrantService;

    @Test
    void updateDescriptionDoesNotClearRequiredFieldsWhenPayloadIsPartial() {
        Movie existing = new Movie();
        existing.setMovieId(1L);
        existing.setTitle("MAI");
        existing.setDuration(131);
        existing.setGenre("Tam ly, Tinh cam");
        existing.setDescription("Mo ta cu");
        existing.setPosterUrl("/api/movies/images/mai.jpg");
        existing.setTrailerUrl("https://youtube.com/watch?v=mai");
        existing.setStatus(Movie.MovieStatus.NOW_SHOWING);
        existing.setAgeRating(Movie.AgeRating.C18);
        existing.setActors("Phuong Anh Dao, Tuan Tran");
        existing.setSearchEmbedding("cached-vector");

        Movie partialUpdate = new Movie();
        partialUpdate.setDescription("Mo ta moi nhieu chi tiet hon de tim kiem thong minh.");

        when(movieRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(movieRepository.save(any(Movie.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Movie updated = new MovieService(movieRepository, qdrantService).update(1L, partialUpdate);

        assertThat(updated.getTitle()).isEqualTo("MAI");
        assertThat(updated.getDuration()).isEqualTo(131);
        assertThat(updated.getStatus()).isEqualTo(Movie.MovieStatus.NOW_SHOWING);
        assertThat(updated.getDescription()).isEqualTo("Mo ta moi nhieu chi tiet hon de tim kiem thong minh.");
        assertThat(updated.getSearchEmbedding()).isNull();

        ArgumentCaptor<Movie> savedMovie = ArgumentCaptor.forClass(Movie.class);
        verify(movieRepository).save(savedMovie.capture());
        assertThat(savedMovie.getValue().getTitle()).isEqualTo("MAI");
        verify(qdrantService).deleteDocument("MOVIE", 1L, "MOVIE:1");
    }
}
