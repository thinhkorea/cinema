package com.example.cinema.repository;

import com.example.cinema.domain.StaffShift;
import com.example.cinema.domain.StaffShiftCapacity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StaffShiftCapacityRepository extends JpaRepository<StaffShiftCapacity, Long> {

    Optional<StaffShiftCapacity> findByWorkDateAndShiftSlot(LocalDate workDate, StaffShift.ShiftSlot shiftSlot);

    List<StaffShiftCapacity> findByWorkDateOrderByShiftSlotAsc(LocalDate workDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select capacity
            from StaffShiftCapacity capacity
            where capacity.workDate = :workDate and capacity.shiftSlot = :shiftSlot
            """)
    Optional<StaffShiftCapacity> findLockedByWorkDateAndShiftSlot(
            @Param("workDate") LocalDate workDate,
            @Param("shiftSlot") StaffShift.ShiftSlot shiftSlot);
}
