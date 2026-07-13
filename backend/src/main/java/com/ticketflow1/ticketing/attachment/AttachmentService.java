package com.ticketflow1.ticketing.attachment;

import com.ticketflow1.ticketing.attachment.dto.AttachmentResponse;
import com.ticketflow1.ticketing.attachment.dto.CreateAttachmentRequest;
import com.ticketflow1.ticketing.audit.AuditAction;
import com.ticketflow1.ticketing.audit.AuditService;
import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.ticket.Responsibility;
import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.ticket.TicketRepository;
import com.ticketflow1.ticketing.user.AppUser;
import com.ticketflow1.ticketing.user.AppUserRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttachmentService {
    private final AttachmentRepository attachmentRepository;
    private final TicketRepository ticketRepository;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;
    private final long maxSizeBytes;

    public AttachmentService(AttachmentRepository attachmentRepository, TicketRepository ticketRepository,
            AppUserRepository appUserRepository, AuditService auditService,
            @Value("${app.attachments.max-size-bytes:104857600}") long maxSizeBytes) {
        this.attachmentRepository = attachmentRepository;
        this.ticketRepository = ticketRepository;
        this.appUserRepository = appUserRepository;
        this.auditService = auditService;
        this.maxSizeBytes = maxSizeBytes;
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(String ticketKey, AuthPrincipal principal) {
        Ticket ticket = findVisibleTicket(ticketKey, principal);
        return attachmentRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()).stream()
                .map(AttachmentResponse::from).toList();
    }

    @Transactional
    public AttachmentResponse create(String ticketKey, CreateAttachmentRequest request, AuthPrincipal principal) {
        Ticket ticket = findVisibleTicket(ticketKey, principal);
        if (request.sizeBytes() > maxSizeBytes) {
            throw ApiException.validation("sizeBytes exceeds the configured limit of " + maxSizeBytes + ".");
        }
        AppUser uploader = appUserRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("Current user no longer exists."));
        Attachment saved = attachmentRepository.saveAndFlush(new Attachment(ticket, uploader,
                request.fileName().trim(), request.contentType().trim(), request.sizeBytes()));
        auditService.record(ticket, uploader.getId(), AuditAction.ATTACHMENT_ADDED,
                "attachment", request.contentType().trim(), saved.getId().toString());
        return AttachmentResponse.from(saved);
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
