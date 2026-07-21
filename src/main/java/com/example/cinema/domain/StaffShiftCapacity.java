package com.example.cinema.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(
        name = "staff_shift_capacities",
        uniqueConstraints = @UniqueConstraint(columnNames = {"work_date", "shift_slot"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffShiftCapacity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long capacityId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift_slot", nullable = false, length = 20)
    private StaffShift.ShiftSlot shiftSlot;

    @Column(name = "max_staff", nullable = false)
    private Integer maxStaff = 1;
}
