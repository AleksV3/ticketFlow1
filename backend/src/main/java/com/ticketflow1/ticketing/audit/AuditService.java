package com.ticketflow1.ticketing.audit;

import com.ticketflow1.ticketing.audit.dto.AuditLogResponse;
import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.ticket.Responsibility;
import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.ticket.TicketRepository;
import com.ticketflow1.ticketing.user.AppUserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AppUserRepository appUserRepository;
    private final TicketRepository ticketRepository;

    public AuditService(AuditLogRepository auditLogRepository, AppUserRepository appUserRepository,
            TicketRepository ticketRepository) {
        this.auditLogRepository = auditLogRepository;
        this.appUserRepository = appUserRepository;
        this.ticketRepository = ticketRepository;
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

    @Transactional(readOnly = true)
    public List<AuditLogResponse> list(String ticketKey, AuthPrincipal principal) {
        Ticket ticket = findVisibleTicket(ticketKey, principal);
        return auditLogRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()).stream()
                .map(AuditLogResponse::from)
                .toList();
    }

    private Ticket findVisibleTicket(String ticketKey, AuthPrincipal principal) {
        if (principal.party() == Responsibility.CLIENT) {
            return ticketRepository.findByTicketKeyAndOrganizationId(ticketKey, principal.organizationId())
                    .orElseThrow(() -> ApiException.notFound("Ticket not found: " + ticketKey));
        }
        return ticketRepository.findByTicketKey(ticketKey)
                .orElseThrow(() -> ApiException.notFound("Ticket not found: " + ticketKey));
    }
}
