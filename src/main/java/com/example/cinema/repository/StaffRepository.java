package com.example.cinema.repository;

import com.example.cinema.domain.Staff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    // 🔍 Tìm nhân viên bằng username (thông qua user)
    Optional<Staff> findByUser_Username(String username);

    // 🔍 Tìm nhân viên bằng userId
    Optional<Staff> findByUser_UserId(Long userId);
}
