package com.ticketflow1.ticketing.audit;

import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AppUserRepository appUserRepository;

    public AuditService(AuditLogRepository auditLogRepository, AppUserRepository appUserRepository) {
        this.auditLogRepository = auditLogRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public AuditLog record(Ticket ticket, Long actorId, AuditAction action) {
        return record(ticket, actorId, action, null, null, null);
    }

    @Transactional
    public AuditLog record(Ticket ticket, Long actorId, AuditAction action, String fieldName,
            String oldValue, String newValue) {
        AuditLog auditLog = new AuditLog(
                ticket,
                appUserRepository.getReferenceById(actorId),
                action,
                fieldName,
                oldValue,
                newValue);
        return auditLogRepository.save(auditLog);
    }
}
