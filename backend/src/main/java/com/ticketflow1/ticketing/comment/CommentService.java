package com.ticketflow1.ticketing.comment;

import com.ticketflow1.ticketing.audit.AuditAction;
import com.ticketflow1.ticketing.audit.AuditService;
import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.comment.dto.CommentResponse;
import com.ticketflow1.ticketing.comment.dto.CreateCommentRequest;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.ticket.TicketService;
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
    private final TicketService ticketService;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;

    public CommentService(CommentRepository commentRepository, TicketService ticketService,
            AppUserRepository appUserRepository, AuditService auditService) {
        this.commentRepository = commentRepository;
        this.ticketService = ticketService;
        this.appUserRepository = appUserRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(String ticketKey, AuthPrincipal principal) {
        Ticket ticket = ticketService.findVisibleTicket(ticketKey, principal);
        List<Comment> comments = principal.hasPermission(INTERNAL_READ)
                ? commentRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId())
                : commentRepository.findByTicketIdAndVisibilityOrderByCreatedAtAsc(
                        ticket.getId(), CommentVisibility.PUBLIC);
        return comments.stream().map(CommentResponse::from).toList();
    }

    @Transactional
    public CommentResponse create(String ticketKey, CreateCommentRequest request, AuthPrincipal principal) {
        Ticket ticket = ticketService.findVisibleTicket(ticketKey, principal);
        if (request.visibility() == CommentVisibility.INTERNAL && !principal.hasPermission(INTERNAL_WRITE)) {
            throw ApiException.forbidden("COMMENT_INTERNAL_WRITE is required for INTERNAL comments.");
        }

        AppUser author = appUserRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("Current user no longer exists."));
        Comment saved = commentRepository.saveAndFlush(
                new Comment(ticket, author, request.body().trim(), request.visibility()));

        // Store only the id and visibility. Comment bodies must never enter the audit feed.
        auditService.record(ticket, author.getId(), AuditAction.COMMENT_ADDED,
                "comment", request.visibility().name(), saved.getId().toString());
        return CommentResponse.from(saved);
    }
}
