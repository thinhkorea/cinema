package com.example.cinema.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "staff_shifts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long shiftId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Column
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ShiftSlot shiftSlot;

    @Column
    private LocalDateTime scheduledStart;

    @Column
    private LocalDateTime scheduledEnd;

    @Column
    private LocalDateTime openedAt;

    @Column
    private LocalDateTime closedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ASSIGNED;

    @Column
    private Double expectedCash = 0.0;

    @Column
    private Double actualCash = 0.0;

    @Column
    private Double cashDifference = 0.0;

    @Column(length = 500)
    private String note;

    @Column
    private Integer lateMinutes = 0;

    @Column
    private Integer earlyLeaveMinutes = 0;

    @Column
    private Integer overtimeMinutes = 0;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AttendanceStatus attendanceStatus = AttendanceStatus.ASSIGNED;

    public enum Status {
        ASSIGNED,
        OPEN,
        CLOSED
    }

    public enum ShiftSlot {
        SHIFT_1,
        SHIFT_2,
        SHIFT_3,
        SHIFT_4
    }

    public enum AttendanceStatus {
        ASSIGNED,
        ON_TIME,
        LATE,
        EARLY_LEAVE,
        LATE_AND_EARLY,
        OVERTIME
    }
}
