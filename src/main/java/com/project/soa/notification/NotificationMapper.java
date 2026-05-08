package com.project.soa.notification;

public final class NotificationMapper {

    private NotificationMapper() {}

    public static NotificationResponseDto toDto(Notification notification) {
        NotificationResponseDto dto = new NotificationResponseDto();
        dto.setId(notification.getId());
        dto.setRecipientUserId(notification.getRecipientUserId());
        dto.setRecipientEmail(notification.getRecipientEmail());
        dto.setType(notification.getType());
        dto.setTitle(notification.getTitle());
        dto.setMessage(notification.getMessage());
        dto.setStatus(notification.getStatus() != null ? notification.getStatus().name() : null);
        dto.setCreatedAt(notification.getCreatedAt());
        dto.setSentAt(notification.getSentAt());
        return dto;
    }
}
