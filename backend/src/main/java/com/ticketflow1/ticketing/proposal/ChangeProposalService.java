package com.ticketflow1.ticketing.proposal;

import com.ticketflow1.ticketing.audit.AuditAction;
import com.ticketflow1.ticketing.audit.AuditService;
import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.comment.CommentService;
import com.ticketflow1.ticketing.comment.CommentVisibility;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.common.InvalidStateException;
import com.ticketflow1.ticketing.proposal.dto.ChangeProposalResponse;
import com.ticketflow1.ticketing.proposal.dto.CreateProposalRequest;
import com.ticketflow1.ticketing.ticket.Responsibility;
import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.ticket.TicketRepository;
import com.ticketflow1.ticketing.user.AppUser;
import com.ticketflow1.ticketing.user.AppUserRepository;
import com.ticketflow1.ticketing.workflow.TicketTransitionService;
import com.ticketflow1.ticketing.workflow.TransitionOperationKind;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangeProposalService {
    private final ChangeProposalRepository proposalRepository;
    private final TicketRepository ticketRepository;
    private final AppUserRepository userRepository;
    private final TicketTransitionService transitionService;
    private final CommentService commentService;
    private final AuditService auditService;

    public ChangeProposalService(ChangeProposalRepository proposalRepository, TicketRepository ticketRepository,
            AppUserRepository userRepository, TicketTransitionService transitionService,
            CommentService commentService, AuditService auditService) {
        this.proposalRepository = proposalRepository; this.ticketRepository = ticketRepository;
        this.userRepository = userRepository; this.transitionService = transitionService;
        this.commentService = commentService; this.auditService = auditService;
    }

    @Transactional
    public ChangeProposalResponse create(String ticketKey, CreateProposalRequest request, AuthPrincipal principal) {
        if (principal.party() != Responsibility.TICKETFLOW1) throw ApiException.forbidden("Only TicketFlow1 may create proposals.");
        Ticket ticket = visibleTicket(ticketKey, principal);
        if (!ticket.getTicketType().isRequiresProposal() || !"ANALYSIS".equals(ticket.getCurrentState().getKey())
                || proposalRepository.existsByTicketIdAndStatus(ticket.getId(), ProposalStatus.PENDING)) {
            throw new InvalidStateException("Ticket cannot accept a proposal in its current state.");
        }
        AppUser actor = actor(principal);
        ChangeProposal saved = proposalRepository.saveAndFlush(new ChangeProposal(ticket,
                request.description().trim(), request.estimatedDeliveryDate(), trim(request.effortEstimate()), actor));
        transitionService.transitionOwned(ticket, TransitionOperationKind.PROPOSAL_CREATE, principal);
        auditService.record(ticket, actor.getId(), AuditAction.PROPOSAL_CREATED,
                "proposal", null, saved.getId().toString());
        return ChangeProposalResponse.from(saved);
    }

    @Transactional
    public ChangeProposalResponse approve(Long id, String comment, AuthPrincipal principal) {
        return decide(id, ProposalStatus.APPROVED, comment, principal);
    }

    @Transactional
    public ChangeProposalResponse reject(Long id, String comment, AuthPrincipal principal) {
        if (comment == null || comment.isBlank()) throw ApiException.validation("A rejection comment is required.");
        return decide(id, ProposalStatus.REJECTED, comment, principal);
    }

    private ChangeProposalResponse decide(Long id, ProposalStatus decision, String comment, AuthPrincipal principal) {
        ChangeProposal proposal = proposalRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Proposal not found: " + id));
        Ticket ticket = proposal.getTicket();
        if (principal.party() != Responsibility.CLIENT
                || !ticket.getOrganization().getId().equals(principal.organizationId())) {
            throw ApiException.notFound("Proposal not found: " + id);
        }
        if (!principal.hasPermission("PROPOSAL_APPROVE")) throw ApiException.forbidden("PROPOSAL_APPROVE is required.");
        if (proposal.getStatus() != ProposalStatus.PENDING) throw new InvalidStateException("Proposal is already decided.");
        AppUser actor = actor(principal);
        proposal.decide(decision, actor, Instant.now());
        TransitionOperationKind operation = decision == ProposalStatus.APPROVED
                ? TransitionOperationKind.PROPOSAL_APPROVE : TransitionOperationKind.PROPOSAL_REJECT;
        transitionService.transitionOwned(ticket, operation, principal);
        if (comment != null && !comment.isBlank()) commentService.createForTicket(ticket, comment, CommentVisibility.PUBLIC, principal);
        auditService.record(ticket, actor.getId(), decision == ProposalStatus.APPROVED
                ? AuditAction.PROPOSAL_APPROVED : AuditAction.PROPOSAL_REJECTED,
                "proposal", "PENDING", decision.name());
        return ChangeProposalResponse.from(proposalRepository.saveAndFlush(proposal));
    }

    private Ticket visibleTicket(String key, AuthPrincipal principal) {
        return ticketRepository.findByTicketKey(key).orElseThrow(() -> ApiException.notFound("Ticket not found: " + key));
    }
    private AppUser actor(AuthPrincipal principal) { return userRepository.findById(principal.userId())
            .orElseThrow(() -> ApiException.notFound("Current user no longer exists.")); }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
