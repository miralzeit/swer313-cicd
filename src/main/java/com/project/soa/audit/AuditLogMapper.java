package com.project.soa.audit;

public final class AuditLogMapper {

    private AuditLogMapper() {}

    public static AuditLogResponseDto toDto(AuditLog auditLog) {
        AuditLogResponseDto dto = new AuditLogResponseDto();
        dto.setId(auditLog.getId());
        dto.setAction(auditLog.getAction());
        dto.setActorUserId(auditLog.getActorUserId());
        dto.setTargetType(auditLog.getTargetType());
        dto.setTargetId(auditLog.getTargetId());
        dto.setDetails(auditLog.getDetails());
        dto.setTimestamp(auditLog.getTimestamp());
        return dto;
    }
}
