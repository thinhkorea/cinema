package com.example.cinema.service;

import com.example.cinema.domain.Movie;
import com.example.cinema.domain.Snack;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class CinemaSearchDocumentBuilder {

    private static final int MOVIE_CHUNK_MIN_DESCRIPTION_LENGTH = 180;
    private static final int MOVIE_CHUNK_SENTENCES_PER_CHUNK = 3;
    private static final int MOVIE_CHUNK_SENTENCE_OVERLAP = 1;
    private static final int MOVIE_CHUNK_WORDS_PER_CHUNK = 90;
    private static final int MOVIE_CHUNK_WORD_OVERLAP = 25;
    private static final int MOVIE_CHUNK_MAX_CHUNKS = 8;
    private static final Pattern SENTENCE_SPLIT_PATTERN = Pattern.compile("\\n+|(?<=[.!?])\\s+");

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

    public List<String> buildMovieChunkSearchDocuments(Movie movie) {
        if (movie == null) return Collections.emptyList();
        String description = safeText(movie.getDescription()).trim();
        if (description.length() < MOVIE_CHUNK_MIN_DESCRIPTION_LENGTH) {
            return Collections.emptyList();
        }

        List<String> chunks = new ArrayList<>();
        List<String> chunkTexts = buildMovieChunkTexts(description);
        for (int index = 0; index < chunkTexts.size() && chunks.size() < MOVIE_CHUNK_MAX_CHUNKS; index++) {
            String chunkText = chunkTexts.get(index).trim();
            if (chunkText.length() < 40) {
                continue;
            }
            chunks.add(buildMovieChunkSearchDocument(movie, chunks.size() + 1, chunkText));
        }
        return chunks;
    }

    private String buildMovieChunkSearchDocument(Movie movie, int chunkNumber, String chunkText) {
        List<String> parts = new ArrayList<>();
        parts.add("Data type: movie plot segment.");
        parts.add("Search purpose: match user-described movie scenes, setting, characters, conflict, sacrifice, and ending.");
        parts.add("Movie title: " + safeText(movie.getTitle()));
        parts.add("Genre: " + safeText(movie.getGenre()));
        parts.add("Actors: " + safeText(movie.getActors()));
        parts.add("Plot segment " + chunkNumber + ": " + chunkText);
        return String.join("\n", parts);
    }

    private List<String> buildMovieChunkTexts(String description) {
        List<String> sentences = splitMovieDescriptionSentences(description);
        if (sentences.size() <= 1) {
            return buildWordChunks(description);
        }

        List<String> chunks = new ArrayList<>();
        int step = Math.max(1, MOVIE_CHUNK_SENTENCES_PER_CHUNK - MOVIE_CHUNK_SENTENCE_OVERLAP);
        for (int start = 0; start < sentences.size() && chunks.size() < MOVIE_CHUNK_MAX_CHUNKS; start += step) {
            int end = Math.min(sentences.size(), start + MOVIE_CHUNK_SENTENCES_PER_CHUNK);
            chunks.add(String.join(" ", sentences.subList(start, end)).trim());
            if (end >= sentences.size()) {
                break;
            }
        }
        return chunks;
    }

    private List<String> splitMovieDescriptionSentences(String description) {
        String normalized = safeText(description)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        if (normalized.isBlank()) {
            return Collections.emptyList();
        }

        List<String> sentences = new ArrayList<>();
        for (String part : SENTENCE_SPLIT_PATTERN.split(normalized)) {
            String sentence = part.trim();
            if (!sentence.isBlank()) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }

    private List<String> buildWordChunks(String description) {
        String[] words = safeText(description).trim().split("\\s+");
        if (words.length <= MOVIE_CHUNK_WORDS_PER_CHUNK) {
            return Collections.singletonList(safeText(description).trim());
        }

        List<String> chunks = new ArrayList<>();
        int step = Math.max(1, MOVIE_CHUNK_WORDS_PER_CHUNK - MOVIE_CHUNK_WORD_OVERLAP);
        for (int start = 0; start < words.length && chunks.size() < MOVIE_CHUNK_MAX_CHUNKS; start += step) {
            int end = Math.min(words.length, start + MOVIE_CHUNK_WORDS_PER_CHUNK);
            chunks.add(String.join(" ", Arrays.copyOfRange(words, start, end)).trim());
            if (end >= words.length) {
                break;
            }
        }
        return chunks;
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
