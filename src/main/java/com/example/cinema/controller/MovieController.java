package com.example.cinema.controller;

import com.example.cinema.domain.Movie;
import com.example.cinema.service.MovieService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieService movieService;
    private static final Path MOVIE_UPLOAD_DIR = Paths.get("uploads", "movies");

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    // Lấy tất cả phim
    @GetMapping
    public ResponseEntity<List<Movie>> getAllMovies() {
        return ResponseEntity.ok(movieService.findAll());
    }

    // Lấy phim theo ID
    @PostMapping("/admin/upload-poster")
    public ResponseEntity<?> uploadMoviePoster(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Vui lòng chọn ảnh poster."));
            }

            String contentType = file.getContentType() == null ? "" : file.getContentType();
            if (!contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body(Map.of("error", "File upload phải là ảnh."));
            }

            String originalName = Paths.get(file.getOriginalFilename() == null ? "poster" : file.getOriginalFilename())
                    .getFileName()
                    .toString();
            String extension = "";
            int dotIndex = originalName.lastIndexOf('.');
            if (dotIndex >= 0) {
                extension = originalName.substring(dotIndex).replaceAll("[^a-zA-Z0-9.]", "").toLowerCase();
            }
            if (extension.isBlank()) {
                extension = ".jpg";
            }

            String fileName = UUID.randomUUID() + extension;
            Path uploadDir = MOVIE_UPLOAD_DIR.toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);
            Files.copy(file.getInputStream(), uploadDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);

            return ResponseEntity.ok(Map.of(
                    "fileName", fileName,
                    "posterUrl", "/api/movies/images/" + fileName));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Không thể upload ảnh poster: " + e.getMessage()));
        }
    }

    @GetMapping("/images/{fileName:.+}")
    public ResponseEntity<Resource> getMoviePoster(@PathVariable String fileName) throws Exception {
        Path uploadDir = MOVIE_UPLOAD_DIR.toAbsolutePath().normalize();
        Path imagePath = uploadDir.resolve(fileName).normalize();
        if (!imagePath.startsWith(uploadDir)) {
            return ResponseEntity.badRequest().build();
        }

        Resource resource = new UrlResource(imagePath.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(imagePath);
        MediaType mediaType = contentType == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(contentType);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                .contentType(mediaType)
                .body(resource);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getMovieById(@PathVariable Long id) {
        return movieService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Lấy phim theo trạng thái (NOW_SHOWING, COMING_SOON)
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Movie>> getMoviesByStatus(@PathVariable String status) {
        return ResponseEntity.ok(movieService.findByStatus(status));
    }

    // Tạo mới phim
    @PostMapping
    public ResponseEntity<Movie> createMovie(@RequestBody Movie movie) {
        return ResponseEntity.ok(movieService.save(movie));
    }

    // Cập nhật phim
    @PutMapping("/{id}")
    public ResponseEntity<?> updateMovie(@PathVariable Long id, @RequestBody Movie movie) {
        try {
            Movie updated = movieService.update(id, movie);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Xoá phim
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMovie(@PathVariable Long id) {
        if (movieService.findById(id).isPresent()) {
            movieService.delete(id);
            return ResponseEntity.ok("Movie deleted successfully.");
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
