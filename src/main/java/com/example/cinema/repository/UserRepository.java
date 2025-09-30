package com.example.cinema.repository;

import com.example.cinema.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Có thể thêm method custom nếu cần
    User findByUsername(String username);
}
