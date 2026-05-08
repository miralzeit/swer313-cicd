package com.project.soa.notification;

import com.project.soa.auth.user.User;
import com.project.soa.booking.Booking;
import com.project.soa.booking.BookingRepository;
import com.project.soa.catalog.Hotel;
import com.project.soa.catalog.RoomType;
import com.project.soa.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock ApplicationEventPublisher eventPublisher;
    @Mock BookingRepository bookingRepository;
    @Mock NotificationRepository notificationRepository;

    Clock fixedClock = Clock.fixed(Instant.parse("2025-06-01T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void handleBookingConfirmed_createsNotificationRecord() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        service().handleBookingConfirmed(new BookingConfirmedEvent(bookingId));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getRecipientUserId()).isEqualTo(booking.getUser().getId());
        assertThat(saved.getType()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(saved.getCreatedAt()).isEqualTo("2025-06-01T10:00:00");
    }

    @Test
    void handleBookingCancelled_createsNotificationRecord() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        service().handleBookingCancelled(new BookingCancelledEvent(bookingId));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("BOOKING_CANCELLED");
    }

    @Test
    void getNotificationsForUser_returnsOwnNotifications() {
        UUID userId = UUID.randomUUID();
        Notification notification = notification(userId);
        when(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(notification));

        List<NotificationResponseDto> result = service().getNotificationsForUser(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRecipientUserId()).isEqualTo(userId);
    }

    @Test
    void markAsRead_otherUsersNotification_throws() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Notification notification = notification(ownerId);
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> service().markAsRead(notification.getId(), otherUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void markAsRead_ownNotification_marksRead() {
        UUID userId = UUID.randomUUID();
        Notification notification = notification(userId);
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponseDto result = service().markAsRead(notification.getId(), userId);

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.READ.name());
    }

    private NotificationServiceImpl service() {
        return new NotificationServiceImpl(eventPublisher, bookingRepository, notificationRepository, fixedClock);
    }

    private Booking booking(UUID bookingId) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("guest@test.com");

        Hotel hotel = new Hotel();
        hotel.setName("Grand Hotel");

        RoomType roomType = new RoomType();
        roomType.setHotel(hotel);

        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setUser(user);
        booking.setRoomType(roomType);
        return booking;
    }

    private Notification notification(UUID userId) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setRecipientUserId(userId);
        notification.setRecipientEmail("guest@test.com");
        notification.setType("BOOKING_CONFIRMED");
        notification.setTitle("Booking confirmed");
        notification.setMessage("Confirmed");
        notification.setStatus(NotificationStatus.UNREAD);
        return notification;
    }
}
