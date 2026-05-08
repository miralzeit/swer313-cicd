package com.project.soa.booking;

import java.util.List;
import java.util.UUID;

public interface BookingInternalService {

    List<Booking> findAllWithHotelDetails();

    boolean hasCompletedStay(UUID userId, UUID hotelId);
}
