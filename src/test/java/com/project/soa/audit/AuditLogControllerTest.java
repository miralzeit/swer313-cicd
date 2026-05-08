package com.project.soa.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    @Mock AuditLogService auditLogService;

    @Test
    void listAuditLogs_delegatesToServiceWithPaginationAndFilters() {
        AuditLogController controller = new AuditLogController(auditLogService);
        UUID actorId = UUID.randomUUID();
        when(auditLogService.findAuditLogs(org.mockito.ArgumentMatchers.eq("BOOKING_CONFIRMED"),
                org.mockito.ArgumentMatchers.eq(actorId),
                org.mockito.ArgumentMatchers.eq("Booking"),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(new AuditLogResponseDto())));

        controller.listAuditLogs("BOOKING_CONFIRMED", actorId, "Booking", 0, 500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogService).findAuditLogs(
                org.mockito.ArgumentMatchers.eq("BOOKING_CONFIRMED"),
                org.mockito.ArgumentMatchers.eq(actorId),
                org.mockito.ArgumentMatchers.eq("Booking"),
                captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }
}
