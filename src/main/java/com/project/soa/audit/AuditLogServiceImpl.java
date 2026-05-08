package com.project.soa.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public AuditLogServiceImpl(AuditLogRepository auditLogRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Override
    public void log(String action, UUID actorUserId, String targetType, UUID targetId, String details) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(action);
        auditLog.setActorUserId(actorUserId);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setDetails(details);
        auditLog.setTimestamp(LocalDateTime.now(clock));
        auditLogRepository.save(auditLog);
    }

    @Override
    public void logCurrentActor(String action, String targetType, UUID targetId, String details) {
        log(action, currentActorId(), targetType, targetId, details);
    }

    @Override
    @Transactional(readOnly = true)
    public UUID currentActorId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponseDto> findAuditLogs(String action, UUID actorUserId, String targetType, Pageable pageable) {
        Specification<AuditLog> spec = Specification.where(null);
        if (action != null && !action.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (actorUserId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorUserId"), actorUserId));
        }
        if (targetType != null && !targetType.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("targetType"), targetType));
        }
        return auditLogRepository.findAll(spec, pageable).map(AuditLogMapper::toDto);
    }
}
