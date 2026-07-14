package com.example.cinema.service;

import com.example.cinema.domain.Movie;
import com.example.cinema.domain.Snack;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Component
public class CinemaSearchDocumentBuilder {

    public String buildMovieSearchDocument(Movie movie) {
        if (movie == null) return "";
        List<String> parts = new ArrayList<>();
        parts.add("Loại dữ liệu: phim điện ảnh trong rạp chiếu phim.");
        parts.add("Mục đích tìm kiếm: gợi ý phim theo tên phim, thể loại, nội dung, diễn viên, độ tuổi, trạng thái và nhu cầu giải trí của khách hàng.");
        parts.add("Tên phim: " + safeText(movie.getTitle()));
        parts.add("Tên phim ưu tiên: " + repeatText(movie.getTitle(), 3));
        parts.add("Thể loại: " + safeText(movie.getGenre()));
        parts.add("Thể loại ưu tiên: " + repeatText(movie.getGenre(), 2));
        parts.add("Mô tả nội dung: " + safeText(movie.getDescription()));
        parts.add("Diễn viên: " + safeText(movie.getActors()));
        parts.add("Thời lượng phút: " + (movie.getDuration() != null ? movie.getDuration() : ""));
        parts.add("Độ tuổi: " + (movie.getAgeRating() != null ? movie.getAgeRating().name() : ""));
        parts.add("Trạng thái: " + buildMovieStatusSearchText(movie.getStatus()));
        return String.join("\n", parts);
    }

    public String buildSnackSearchDocument(Snack snack) {
        if (snack == null) return "";
        List<String> parts = new ArrayList<>();
        parts.add("Loại dữ liệu: bắp nước, đồ ăn, đồ uống, combo trong rạp chiếu phim.");
        parts.add("Mục đích tìm kiếm: gợi ý món ăn, nước uống, combo theo nhu cầu ăn vặt, giá, loại sản phẩm và trạng thái bán hàng.");
        parts.add("Tên sản phẩm: " + safeText(snack.getSnackName()));
        parts.add("Tên sản phẩm ưu tiên: " + repeatText(snack.getSnackName(), 3));
        parts.add("Danh mục: " + (snack.getCategory() != null ? snack.getCategory().name() : ""));
        parts.add("Danh mục ưu tiên: " + (snack.getCategory() != null ? repeatText(snack.getCategory().name(), 2) : ""));
        parts.add("Mô tả: " + safeText(snack.getDescription()));
        parts.add("Giá VND: " + (snack.getPrice() != null ? String.format(Locale.ROOT, "%.0f", snack.getPrice()) : ""));
        parts.add("Trạng thái: " + (Boolean.TRUE.equals(snack.getAvailable()) ? "đang bán, còn hàng, available" : "tạm hết, không bán, unavailable"));
        parts.add("Tồn kho: " + (snack.getWarehouseStock() != null ? snack.getWarehouseStock() : ""));
        return String.join("\n", parts);
    }

    private String buildMovieStatusSearchText(Movie.MovieStatus status) {
        if (status == null) return "";
        switch (status) {
            case NOW_SHOWING:
                return "đang chiếu, now showing, có thể đặt vé";
            case COMING_SOON:
                return "sắp chiếu, coming soon, chưa mở bán";
            case SPECIAL_RELEASE:
                return "suất chiếu đặc biệt, special release";
            case ENDED:
                return "đã kết thúc, ended, không còn chiếu";
            default:
                return status.name();
        }
    }

    private String repeatText(String value, int times) {
        String text = safeText(value);
        if (text.isBlank() || times <= 0) return "";
        return String.join(" ", Collections.nCopies(times, text));
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

}
