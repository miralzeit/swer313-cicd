package com.project.soa.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceImplTest {

    @Mock AuditLogRepository auditLogRepository;

    Clock fixedClock = Clock.fixed(Instant.parse("2025-06-01T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void log_savesAuditLog() {
        AuditLogServiceImpl service = new AuditLogServiceImpl(auditLogRepository, fixedClock);
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        service.log("BOOKING_CONFIRMED", actorId, "Booking", targetId, "Booking confirmed");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(saved.getActorUserId()).isEqualTo(actorId);
        assertThat(saved.getTargetType()).isEqualTo("Booking");
        assertThat(saved.getTargetId()).isEqualTo(targetId);
        assertThat(saved.getTimestamp()).isEqualTo("2025-06-01T10:00:00");
    }

    @Test
    void findAuditLogs_returnsMappedPage() {
        AuditLogServiceImpl service = new AuditLogServiceImpl(auditLogRepository, fixedClock);
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setAction("BOOKING_CONFIRMED");
        log.setTargetType("Booking");
        log.setTargetId(UUID.randomUUID());
        when(auditLogRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(log)));

        var result = service.findAuditLogs("BOOKING_CONFIRMED", null, "Booking", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAction()).isEqualTo("BOOKING_CONFIRMED");
    }
}
