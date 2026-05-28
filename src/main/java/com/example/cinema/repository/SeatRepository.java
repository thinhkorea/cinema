package com.example.cinema.repository;

import com.example.cinema.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByRoom_RoomId(Long roomId);

    @Query("SELECT s FROM Seat s WHERE s.room.roomId = :roomId AND (s.active = true OR s.active IS NULL)")
    List<Seat> findByRoom_RoomIdAndActiveTrue(Long roomId);

    List<Seat> findByRoom_RoomIdOrderBySeatNumberAsc(Long roomId);

    @Query("""
           SELECT s
           FROM Seat s
           WHERE s.room.roomId = :roomId
             AND (s.active = true OR s.active IS NULL)
           ORDER BY s.seatNumber ASC
           """)
    List<Seat> findByRoom_RoomIdAndActiveTrueOrderBySeatNumberAsc(Long roomId);

    @Query("""
           SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
           FROM Seat s
           WHERE s.room.roomId = :roomId
             AND LOWER(s.seatNumber) = LOWER(:seatNumber)
             AND (s.active = true OR s.active IS NULL)
           """)
    boolean existsByRoom_RoomIdAndSeatNumberIgnoreCaseAndActiveTrue(
            @Param("roomId") Long roomId,
            @Param("seatNumber") String seatNumber);

    @Query("""
           SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
           FROM Seat s
           WHERE s.room.roomId = :roomId
             AND LOWER(s.seatNumber) = LOWER(:seatNumber)
             AND (s.active = true OR s.active IS NULL)
             AND s.seatId <> :seatId
           """)
    boolean existsByRoom_RoomIdAndSeatNumberIgnoreCaseAndActiveTrueAndSeatIdNot(
            @Param("roomId") Long roomId,
            @Param("seatNumber") String seatNumber,
            @Param("seatId") Long seatId);

    void deleteByRoom_RoomId(Long roomId);
}
