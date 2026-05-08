package com.project.soa.catalog;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    List<Room> findByRoomTypeIdOrderByRoomNumberAsc(UUID roomTypeId);

    boolean existsByHotelIdAndRoomNumber(UUID hotelId, String roomNumber);

    boolean existsByHotelIdAndRoomNumberAndIdNot(UUID hotelId, String roomNumber, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r " +
            "WHERE r.roomType.id = :roomTypeId " +
            "AND r.status = com.project.soa.catalog.RoomStatus.AVAILABLE " +
            "AND NOT EXISTS (" +
            "  SELECT b FROM Booking b " +
            "  WHERE b.room.id = r.id " +
            "  AND b.status = com.project.soa.booking.BookingStatus.CHECKED_IN " +
            "  AND b.checkIn < :checkOut " +
            "  AND b.checkOut > :checkIn" +
            ") " +
            "ORDER BY r.roomNumber ASC")
    List<Room> findAvailableRoomsForAssignment(@Param("roomTypeId") UUID roomTypeId,
                                               @Param("checkIn") LocalDate checkIn,
                                               @Param("checkOut") LocalDate checkOut);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") UUID id);
}
