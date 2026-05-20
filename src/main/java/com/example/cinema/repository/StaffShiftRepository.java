package com.example.cinema.repository;

import com.example.cinema.domain.Staff;
import com.example.cinema.domain.StaffShift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StaffShiftRepository extends JpaRepository<StaffShift, Long> {

    Optional<StaffShift> findFirstByStaffAndStatusOrderByOpenedAtDesc(Staff staff, StaffShift.Status status);

    List<StaffShift> findByStaffAndOpenedAtBetweenOrderByOpenedAtDesc(
            Staff staff,
            LocalDateTime from,
            LocalDateTime to);

    List<StaffShift> findByOpenedAtBetweenOrderByOpenedAtDesc(LocalDateTime from, LocalDateTime to);
}
