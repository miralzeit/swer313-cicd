package com.project.soa.notification;

import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface NotificationService {
    void sendBookingConfirmed(UUID bookingId);
    void sendBookingCancelled(UUID bookingId);

    List<NotificationResponseDto> getNotificationsForUser(UUID userId);

    NotificationResponseDto markAsRead(UUID notificationId, UUID userId);

    Page<NotificationResponseDto> getAllNotifications(Pageable pageable);
}
