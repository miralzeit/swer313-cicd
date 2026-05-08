package com.project.soa.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AuditLogService {

    void log(String action, UUID actorUserId, String targetType, UUID targetId, String details);

    void logCurrentActor(String action, String targetType, UUID targetId, String details);

    UUID currentActorId();

    Page<AuditLogResponseDto> findAuditLogs(String action, UUID actorUserId, String targetType, Pageable pageable);
}
