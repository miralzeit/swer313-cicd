package com.project.soa.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/api/users/me/notifications")
    @PreAuthorize("isAuthenticated()")
    public List<NotificationResponseDto> getMyNotifications(@AuthenticationPrincipal Jwt jwt) {
        return notificationService.getNotificationsForUser(UUID.fromString(jwt.getSubject()));
    }

    @PatchMapping("/api/users/me/notifications/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public NotificationResponseDto markMyNotificationAsRead(@PathVariable UUID id,
                                                            @AuthenticationPrincipal Jwt jwt) {
        return notificationService.markAsRead(id, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/api/admin/notifications")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<NotificationResponseDto> getAllNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(size, 100);
        return notificationService.getAllNotifications(
                PageRequest.of(page, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt")));
    }
}
