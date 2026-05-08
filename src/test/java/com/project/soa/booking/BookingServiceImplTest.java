package com.project.soa.booking;

import com.project.soa.auth.user.User;
import com.project.soa.auth.user.UserInternalService;
import com.project.soa.audit.AuditLogService;
import com.project.soa.availability_pricing.PricingService;
import com.project.soa.catalog.CatalogInternalService;
import com.project.soa.catalog.CatalogStatus;
import com.project.soa.catalog.Hotel;
import com.project.soa.catalog.Room;
import com.project.soa.catalog.RoomStatus;
import com.project.soa.catalog.RoomType;
import com.project.soa.common.exception.BusinessRuleException;
import com.project.soa.common.exception.ResourceNotFoundException;
import com.project.soa.notification.NotificationService;
import com.project.soa.payment.PaymentInternalService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.time.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock UserInternalService userService;
    @Mock BookingRepository bookingRepository;
    @Mock CatalogInternalService catalogInternalService;
    @Mock PricingService pricingService;
    @Mock NotificationService notificationService;
    @Mock PaymentInternalService paymentService;
    @Mock AuditLogService auditLogService;

    Clock fixedClock = Clock.fixed(Instant.parse("2025-06-01T10:00:00Z"), ZoneOffset.UTC);

    @InjectMocks BookingServiceImpl service;

    UUID guestId    = UUID.randomUUID();
    UUID managerId  = UUID.randomUUID();
    UUID roomTypeId = UUID.randomUUID();
    UUID bookingId  = UUID.randomUUID();

    User guest;
    User manager;
    Hotel hotel;
    RoomType roomType;

    @BeforeEach
    void setUp() {
        // Inject fixed clock via reflection since @InjectMocks picks constructor
        service = new BookingServiceImpl(
                userService, bookingRepository, catalogInternalService,
                pricingService, notificationService, paymentService, auditLogService, fixedClock);

        guest = user(guestId);
        manager = user(managerId);

        hotel = new Hotel();
        hotel.setId(UUID.randomUUID());
        hotel.setStatus(CatalogStatus.ACTIVE);
        hotel.setManager(manager);

        roomType = new RoomType();
        roomType.setId(roomTypeId);
        roomType.setCapacity(2);
        roomType.setTotalRooms(5);
        roomType.setStatus(CatalogStatus.ACTIVE);
        roomType.setHotel(hotel);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    // ── createBooking ─────────────────────────────────────────────────────────

    @Test
    void createBooking_happyPath_returnsPendingBooking() {
        authenticateAs(guestId, "GUEST");
        LocalDate in  = LocalDate.of(2025, 7, 1);
        LocalDate out = LocalDate.of(2025, 7, 5);

        when(userService.getById(guestId)).thenReturn(guest);
        when(catalogInternalService.getRoomTypeForUpdate(roomTypeId)).thenReturn(roomType);
        when(pricingService.isFullyBooked(roomTypeId, in, out, null)).thenReturn(false);
        when(pricingService.calculateTotalPrice(roomTypeId, in, out)).thenReturn(new BigDecimal("400.00"));
        when(bookingRepository.save(any())).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(bookingId);
            return b;
        });

        Booking result = service.createBooking(dto(roomTypeId, in, out, 2));

        assertThat(result.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(result.getTotalPrice()).isEqualByComparingTo("400.00");
        assertThat(result.getPendingExpiresAt()).isNotNull();
    }

    @Test
    void createBooking_checkoutBeforeCheckin_throws() {
        authenticateAs(guestId, "GUEST");
        LocalDate in  = LocalDate.of(2025, 7, 5);
        LocalDate out = LocalDate.of(2025, 7, 1);

        assertThatThrownBy(() -> service.createBooking(dto(roomTypeId, in, out, 2)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Check-out must be after check-in");
    }

    @Test
    void createBooking_roomTypeInactive_throws() {
        authenticateAs(guestId, "GUEST");
        roomType.setStatus(CatalogStatus.INACTIVE);
        when(userService.getById(guestId)).thenReturn(guest);
        when(catalogInternalService.getRoomTypeForUpdate(roomTypeId)).thenReturn(roomType);

        assertThatThrownBy(() -> service.createBooking(dto(roomTypeId, future(1), future(3), 2)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not available for booking");
    }

    @Test
    void createBooking_hotelInactive_throws() {
        authenticateAs(guestId, "GUEST");
        hotel.setStatus(CatalogStatus.INACTIVE);
        when(userService.getById(guestId)).thenReturn(guest);
        when(catalogInternalService.getRoomTypeForUpdate(roomTypeId)).thenReturn(roomType);

        assertThatThrownBy(() -> service.createBooking(dto(roomTypeId, future(1), future(3), 2)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not currently accepting bookings");
    }

    @Test
    void createBooking_capacityExceeded_throws() {
        authenticateAs(guestId, "GUEST");
        roomType.setCapacity(1);
        when(userService.getById(guestId)).thenReturn(guest);
        when(catalogInternalService.getRoomTypeForUpdate(roomTypeId)).thenReturn(roomType);

        assertThatThrownBy(() -> service.createBooking(dto(roomTypeId, future(1), future(3), 2)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceeds room capacity");
    }

    @Test
    void createBooking_fullyBooked_throws() {
        authenticateAs(guestId, "GUEST");
        LocalDate in  = future(1);
        LocalDate out = future(3);
        when(userService.getById(guestId)).thenReturn(guest);
        when(catalogInternalService.getRoomTypeForUpdate(roomTypeId)).thenReturn(roomType);
        when(pricingService.isFullyBooked(roomTypeId, in, out, null)).thenReturn(true);

        assertThatThrownBy(() -> service.createBooking(dto(roomTypeId, in, out, 2)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No rooms available");
    }

    // ── confirmBooking ────────────────────────────────────────────────────────

    @Test
    void confirmBooking_notTheManager_throws() {
        authenticateAs(UUID.randomUUID(), "MANAGER");
        when(userService.getById(any())).thenReturn(user(UUID.randomUUID()));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(pendingBooking()));

        assertThatThrownBy(() -> service.confirmBooking(bookingId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("do not manage");
    }

    @Test
    void confirmBooking_notPendingStatus_throws() {
        authenticateAs(managerId, "MANAGER");
        when(userService.getById(managerId)).thenReturn(manager);
        Booking b = pendingBooking();
        b.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.confirmBooking(bookingId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only PENDING");
    }

    @Test
    void confirmBooking_pendingExpired_throws() {
        authenticateAs(managerId, "MANAGER");
        when(userService.getById(managerId)).thenReturn(manager);
        Booking b = pendingBooking();
        // Expired 1 hour ago
        b.setPendingExpiresAt(LocalDateTime.now(fixedClock).minusHours(1));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.confirmBooking(bookingId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("reservation expired");
        assertThat(b.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(b.getPendingExpiresAt()).isNull();
        verify(bookingRepository).save(b);
    }

    @Test
    void confirmBooking_notPaid_throws() {
        authenticateAs(managerId, "MANAGER");
        when(userService.getById(managerId)).thenReturn(manager);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(pendingBooking()));
        when(paymentService.isBookingPaid(bookingId)).thenReturn(false);

        assertThatThrownBy(() -> service.confirmBooking(bookingId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not paid");
    }

    @Test
    void confirmBooking_success_sendsNotification() {
        authenticateAs(managerId, "MANAGER");
        when(userService.getById(managerId)).thenReturn(manager);
        Booking b = pendingBooking();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));
        when(paymentService.isBookingPaid(bookingId)).thenReturn(true);
        when(pricingService.isFullyBooked(any(), any(), any(), eq(bookingId))).thenReturn(false);
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Booking result = service.confirmBooking(bookingId);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(result.getPendingExpiresAt()).isNull();
        verify(notificationService).sendBookingConfirmed(bookingId);
        verify(auditLogService).log("BOOKING_CONFIRMED", managerId, "Booking", bookingId, "Booking confirmed");
    }

    // ── cancelBooking ─────────────────────────────────────────────────────────

    @Test
    void checkInBooking_confirmedBooking_setsCheckedIn() {
        authenticateAs(managerId, "MANAGER");
        when(userService.getById(managerId)).thenReturn(manager);
        Booking b = confirmedBooking();
        Room room = room("101");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));
        when(catalogInternalService.assignAvailableRoom(roomTypeId, b.getCheckIn(), b.getCheckOut()))
                .thenReturn(room);
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Booking result = service.checkInBooking(bookingId);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(result.getRoom()).isEqualTo(room);
        verify(catalogInternalService).assignAvailableRoom(roomTypeId, b.getCheckIn(), b.getCheckOut());
        verify(bookingRepository).save(b);
    }

    @Test
    void checkInBooking_noAvailableRoom_throws() {
        authenticateAs(managerId, "MANAGER");
        when(userService.getById(managerId)).thenReturn(manager);
        Booking b = confirmedBooking();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));
        when(catalogInternalService.assignAvailableRoom(roomTypeId, b.getCheckIn(), b.getCheckOut()))
                .thenThrow(new BusinessRuleException("No available rooms for this room type"));

        assertThatThrownBy(() -> service.checkInBooking(bookingId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No available rooms");
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(b.getRoom()).isNull();
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void checkInBooking_roomAlreadyAssignedToOverlappingBooking_throws() {
        authenticateAs(managerId, "MANAGER");
        when(userService.getById(managerId)).thenReturn(manager);
        Booking b = confirmedBooking();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));
        when(catalogInternalService.assignAvailableRoom(roomTypeId, b.getCheckIn(), b.getCheckOut()))
                .thenThrow(new BusinessRuleException("No available rooms for this room type"));

        assertThatThrownBy(() -> service.checkInBooking(bookingId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No available rooms");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void checkInBooking_pendingBooking_throws() {
        authenticateAs(managerId, "MANAGER");
        when(userService.getById(managerId)).thenReturn(manager);
        Booking b = pendingBooking();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.checkInBooking(bookingId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only CONFIRMED");
    }

    @Test
    void checkInBooking_cancelledBooking_throws() {
        authenticateAs(managerId, "MANAGER");
        when(userService.getById(managerId)).thenReturn(manager);
        Booking b = confirmedBooking();
        b.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.checkInBooking(bookingId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only CONFIRMED");
    }

    @Test
    void checkInBooking_expiredBooking_throws() {
        authenticateAs(managerId, "MANAGER");
        when(userService.getById(managerId)).thenReturn(manager);
        Booking b = confirmedBooking();
        b.setStatus(BookingStatus.EXPIRED);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.checkInBooking(bookingId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only CONFIRMED");
    }

    @Test
    void checkOutBooking_checkedInBooking_setsCheckedOut() {
        authenticateAs(managerId, "MANAGER");
        when(userService.getById(managerId)).thenReturn(manager);
        Booking b = confirmedBooking();
        b.setStatus(BookingStatus.CHECKED_IN);
        Room room = room("101");
        b.setRoom(room);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Booking result = service.checkOutBooking(bookingId);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CHECKED_OUT);
        assertThat(result.getRoom()).isNull();
        verify(catalogInternalService).releaseRoom(room.getId());
        verify(bookingRepository).save(b);
    }

    @Test
    void checkOutBooking_confirmedBooking_throws() {
        authenticateAs(managerId, "MANAGER");
        when(userService.getById(managerId)).thenReturn(manager);
        Booking b = confirmedBooking();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.checkOutBooking(bookingId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only CHECKED_IN");
    }

    @Test
    void checkOutBooking_cancelledBooking_throws() {
        authenticateAs(managerId, "MANAGER");
        when(userService.getById(managerId)).thenReturn(manager);
        Booking b = confirmedBooking();
        b.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.checkOutBooking(bookingId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only CHECKED_IN");
    }

    @Test
    void cancelBooking_alreadyCancelled_throws() {
        Booking b = pendingBooking();
        b.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.cancelBooking(bookingId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Already cancelled");
        verifyNoInteractions(paymentService);
    }

    @Test
    void cancelBooking_expiredBooking_throws() {
        Booking b = pendingBooking();
        b.setStatus(BookingStatus.EXPIRED);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.cancelBooking(bookingId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Expired bookings cannot be cancelled");
        verifyNoInteractions(paymentService);
    }

    @Test
    void cancelBooking_checkedInBooking_throws() {
        Booking b = confirmedBooking();
        b.setStatus(BookingStatus.CHECKED_IN);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.cancelBooking(bookingId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Checked-in bookings cannot be cancelled");
        verifyNoInteractions(paymentService);
    }

    @Test
    void cancelBooking_checkedOutBooking_throws() {
        Booking b = confirmedBooking();
        b.setStatus(BookingStatus.CHECKED_OUT);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.cancelBooking(bookingId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Checked-out bookings cannot be cancelled");
        verifyNoInteractions(paymentService);
    }

    @Test
    void cancelBooking_onCheckinDay_throws() {
        Booking b = pendingBooking();
        // Check-in is today per fixed clock (2025-06-01)
        b.setCheckIn(LocalDate.of(2025, 6, 1));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.cancelBooking(bookingId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("on or after check-in");
    }

    @Test
    void cancelBooking_sevenPlusDaysBefore_fullRefund() {
        Booking b = pendingBooking();
        b.setCheckIn(LocalDate.of(2025, 6, 10)); // 9 days from fixedClock date
        b.setTotalPrice(new BigDecimal("500.00"));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));
        when(bookingRepository.save(any())).thenReturn(b);

        CancellationResultDto result = service.cancelBooking(bookingId);

        assertThat(result.refundAmount()).isEqualByComparingTo("500.00");
        assertThat(result.penaltyAmount()).isEqualByComparingTo("0.00");
        assertThat(result.policyApplied()).contains("Full refund");
        verify(notificationService).sendBookingCancelled(bookingId);
    }

    @Test
    void cancelBooking_confirmedSevenPlusDaysBefore_fullRefund() {
        Booking b = confirmedBooking();
        b.setCheckIn(LocalDate.of(2025, 6, 10)); // 9 days from fixedClock date
        b.setTotalPrice(new BigDecimal("500.00"));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));
        when(bookingRepository.save(any())).thenReturn(b);

        CancellationResultDto result = service.cancelBooking(bookingId);

        assertThat(result.bookingStatus()).isEqualTo(BookingStatus.CANCELLED.name());
        assertThat(result.refundAmount()).isEqualByComparingTo("500.00");
        assertThat(result.penaltyAmount()).isEqualByComparingTo("0.00");
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(paymentService).handleBookingCancellation(bookingId, BigDecimal.ZERO);
        verify(notificationService).sendBookingCancelled(bookingId);
    }

    @Test
    void cancelBooking_threeToSixDaysBefore_fiftyPercentRefund() {
        Booking b = pendingBooking();
        b.setCheckIn(LocalDate.of(2025, 6, 5)); // 4 days from fixedClock date
        b.setTotalPrice(new BigDecimal("200.00"));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));
        when(bookingRepository.save(any())).thenReturn(b);

        CancellationResultDto result = service.cancelBooking(bookingId);

        assertThat(result.refundAmount()).isEqualByComparingTo("100.00");
        assertThat(result.penaltyAmount()).isEqualByComparingTo("100.00");
        assertThat(result.policyApplied()).contains("50%");
    }

    @Test
    void cancelBooking_lessThanThreeDaysBefore_noRefund() {
        Booking b = pendingBooking();
        b.setCheckIn(LocalDate.of(2025, 6, 3)); // 2 days from fixedClock date
        b.setTotalPrice(new BigDecimal("300.00"));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));
        when(bookingRepository.save(any())).thenReturn(b);

        CancellationResultDto result = service.cancelBooking(bookingId);

        assertThat(result.refundAmount()).isEqualByComparingTo("0.00");
        assertThat(result.penaltyAmount()).isEqualByComparingTo("300.00");
        assertThat(result.policyApplied()).contains("No refund");
    }

    // ── BookingInternalService ────────────────────────────────────────────────

    @Test
    void findAllWithHotelDetails_delegatesToRepository() {
        Booking b = pendingBooking();
        when(bookingRepository.findAllWithHotelDetails()).thenReturn(List.of(b));

        assertThat(service.findAllWithHotelDetails()).containsExactly(b);
    }

    @Test
    void hasCompletedStay_delegatesToRepository() {
        UUID hotelId = hotel.getId();
        when(bookingRepository.existsCompletedStay(guestId, hotelId)).thenReturn(true);

        assertThat(service.hasCompletedStay(guestId, hotelId)).isTrue();
    }

    @Test
    void expirePendingBookings_marksExpiredPendingBookingsExpired() {
        Booking expired = pendingBooking();
        expired.setPendingExpiresAt(LocalDateTime.now(fixedClock).minusMinutes(1));
        when(bookingRepository.findByStatusAndPendingExpiresAtBefore(
                eq(BookingStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(expired));

        int result = service.expirePendingBookings();

        assertThat(result).isEqualTo(1);
        assertThat(expired.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(expired.getPendingExpiresAt()).isNull();
        verify(bookingRepository).saveAll(List.of(expired));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Booking pendingBooking() {
        Booking b = new Booking();
        b.setId(bookingId);
        b.setStatus(BookingStatus.PENDING);
        b.setCheckIn(LocalDate.of(2025, 7, 1));
        b.setCheckOut(LocalDate.of(2025, 7, 5));
        b.setTotalPrice(new BigDecimal("400.00"));
        b.setPendingExpiresAt(LocalDateTime.now(fixedClock).plusMinutes(15));
        b.setRoomType(roomType);
        b.setUser(guest);
        return b;
    }

    private Booking confirmedBooking() {
        Booking b = pendingBooking();
        b.setStatus(BookingStatus.CONFIRMED);
        b.setPendingExpiresAt(null);
        return b;
    }

    private Room room(String roomNumber) {
        Room room = new Room();
        room.setId(UUID.randomUUID());
        room.setRoomNumber(roomNumber);
        room.setStatus(RoomStatus.AVAILABLE);
        room.setRoomType(roomType);
        room.setHotel(hotel);
        return room;
    }

    private CreateBookingRequestDto dto(UUID rtId, LocalDate in, LocalDate out, int guests) {
        CreateBookingRequestDto dto = new CreateBookingRequestDto();
        dto.setRoomTypeId(rtId);
        dto.setCheckIn(in);
        dto.setCheckOut(out);
        dto.setNumberOfGuests(guests);
        return dto;
    }

    private LocalDate future(int days) {
        return LocalDate.now(fixedClock).plusDays(days);
    }

    private User user(UUID id) {
        User u = new User();
        u.setId(id);
        u.setEmail(id + "@test.com");
        return u;
    }

    private void authenticateAs(UUID userId, String role) {
        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
