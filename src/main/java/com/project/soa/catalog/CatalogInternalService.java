package com.project.soa.catalog;

import java.util.Optional;
import java.time.LocalDate;
import java.util.UUID;

public interface CatalogInternalService {

    RoomType getRoomTypeForUpdate(UUID id);

    Room assignAvailableRoom(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut);

    void releaseRoom(UUID roomId);

    Optional<Hotel> findHotelById(UUID id);

    long countHotels();
}
