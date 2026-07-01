package com.example.cinema.service;

import com.example.cinema.domain.Movie;
import com.example.cinema.domain.Snack;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CinemaSearchDocumentBuilderTest {

    private final CinemaSearchDocumentBuilder builder = new CinemaSearchDocumentBuilder();

    @Test
    void buildsMovieDocumentWithSearchContext() {
        Movie movie = new Movie();
        movie.setTitle("Mai");
        movie.setGenre("Tình cảm");
        movie.setDescription("Câu chuyện gia đình nhẹ nhàng.");
        movie.setActors("Diễn viên A");
        movie.setDuration(120);
        movie.setAgeRating(Movie.AgeRating.C13);
        movie.setStatus(Movie.MovieStatus.NOW_SHOWING);

        String document = builder.buildMovieSearchDocument(movie);

        assertThat(document)
                .contains("Loại dữ liệu: phim")
                .contains("Tên phim: Mai")
                .contains("Thể loại: Tình cảm")
                .contains("đang chiếu");
    }

    @Test
    void buildsSnackDocumentWithOperationalContext() {
        Snack snack = new Snack();
        snack.setSnackName("Combo Couple");
        snack.setCategory(Snack.SnackCategory.COMBO);
        snack.setDescription("Bắp và nước cho hai người.");
        snack.setPrice(99000.0);
        snack.setAvailable(true);
        snack.setWarehouseStock(20.0);

        String document = builder.buildSnackSearchDocument(snack);

        assertThat(document)
                .contains("Loại dữ liệu: bắp nước")
                .contains("Tên sản phẩm: Combo Couple")
                .contains("Danh mục: COMBO")
                .contains("đang bán");
    }
}
