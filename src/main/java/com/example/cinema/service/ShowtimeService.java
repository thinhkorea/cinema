package com.example.cinema.service;

import com.example.cinema.domain.Showtime;
import com.example.cinema.repository.ShowtimeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ShowtimeService {
    private final ShowtimeRepository showtimeRepository;

    public ShowtimeService(ShowtimeRepository showtimeRepository) {
        this.showtimeRepository = showtimeRepository;
    }

    public List<Showtime> findAll() {
        return showtimeRepository.findAll();
    }

    public Optional<Showtime> findById(Long id) {
        return showtimeRepository.findById(id);
    }

    public List<Showtime> findByMovieId(Long movieId) {
        return showtimeRepository.findByMovie_MovieId(movieId);
    }

    public Showtime save(Showtime showtime) {
        return showtimeRepository.save(showtime);
    }

    public void delete(Long id) {
        showtimeRepository.deleteById(id);
    }
}
