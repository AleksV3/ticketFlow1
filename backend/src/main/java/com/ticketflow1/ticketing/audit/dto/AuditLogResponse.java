package com.ticketflow1.ticketing.audit.dto;

import com.ticketflow1.ticketing.audit.AuditLog;
import java.time.Instant;

public record AuditLogResponse(
        Long id,
        UserRef actor,
        String action,
        String fieldName,
        String oldValue,
        String newValue,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                UserRef.from(auditLog.getActor()),
                auditLog.getAction().name(),
                auditLog.getFieldName(),
                auditLog.getOldValue(),
                auditLog.getNewValue(),
                auditLog.getCreatedAt());
    }

    public record UserRef(Long id, String displayName) {
        public static UserRef from(com.ticketflow1.ticketing.user.AppUser user) {
            return new UserRef(user.getId(), user.getDisplayName());
        }
    }
}
