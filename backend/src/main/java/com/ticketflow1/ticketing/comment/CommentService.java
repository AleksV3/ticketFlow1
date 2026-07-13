package com.ticketflow1.ticketing.comment;

import com.ticketflow1.ticketing.audit.AuditAction;
import com.ticketflow1.ticketing.audit.AuditService;
import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.comment.dto.CommentResponse;
import com.ticketflow1.ticketing.comment.dto.CreateCommentRequest;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.ticket.Responsibility;
import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.ticket.TicketRepository;
import com.ticketflow1.ticketing.user.AppUser;
import com.ticketflow1.ticketing.user.AppUserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    public static final String INTERNAL_READ = "COMMENT_INTERNAL_READ";
    public static final String INTERNAL_WRITE = "COMMENT_INTERNAL_WRITE";

    private final CommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;

    public CommentService(CommentRepository commentRepository, TicketRepository ticketRepository,
            AppUserRepository appUserRepository, AuditService auditService) {
        this.commentRepository = commentRepository;
        this.ticketRepository = ticketRepository;
        this.appUserRepository = appUserRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(String ticketKey, AuthPrincipal principal) {
        Ticket ticket = findVisibleTicket(ticketKey, principal);
        List<Comment> comments = principal.hasPermission(INTERNAL_READ)
                ? commentRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId())
                : commentRepository.findByTicketIdAndVisibilityOrderByCreatedAtAsc(
                        ticket.getId(), CommentVisibility.PUBLIC);
        return comments.stream().map(CommentResponse::from).toList();
    }

    @Transactional
    public CommentResponse create(String ticketKey, CreateCommentRequest request, AuthPrincipal principal) {
        Ticket ticket = findVisibleTicket(ticketKey, principal);
        if (request.visibility() == CommentVisibility.INTERNAL && !principal.hasPermission(INTERNAL_WRITE)) {
            throw ApiException.forbidden("COMMENT_INTERNAL_WRITE is required for INTERNAL comments.");
        }

        return createForTicket(ticket, request.body(), request.visibility(), principal);
    }

    /** Used by owning domain operations that already resolved and locked the ticket. */
    @Transactional
    public CommentResponse createForTicket(Ticket ticket, String body, CommentVisibility visibility,
            AuthPrincipal principal) {
        if (body == null || body.isBlank() || body.trim().length() > 10000) {
            throw ApiException.validation("Comment body must contain between 1 and 10000 characters.");
        }
        AppUser author = appUserRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("Current user no longer exists."));
        Comment saved = commentRepository.saveAndFlush(new Comment(ticket, author, body.trim(), visibility));
        auditService.record(ticket, author.getId(), AuditAction.COMMENT_ADDED,
                "comment", visibility.name(), saved.getId().toString());
        return CommentResponse.from(saved);
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
