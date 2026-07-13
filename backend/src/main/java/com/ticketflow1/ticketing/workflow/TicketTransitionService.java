package com.ticketflow1.ticketing.workflow;

import com.ticketflow1.ticketing.audit.AuditAction;
import com.ticketflow1.ticketing.audit.AuditService;
import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.common.IllegalTransitionException;
import com.ticketflow1.ticketing.statushistory.StatusHistoryService;
import com.ticketflow1.ticketing.ticket.Responsibility;
import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.ticket.TicketRepository;
import com.ticketflow1.ticketing.ticket.dto.TicketDetailResponse;
import com.ticketflow1.ticketing.user.AppUser;
import com.ticketflow1.ticketing.user.AppUserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketTransitionService {

    private final TicketRepository ticketRepository;
    private final WorkflowStateRepository workflowStateRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;
    private final StatusHistoryService statusHistoryService;

    public TicketTransitionService(TicketRepository ticketRepository,
            WorkflowStateRepository workflowStateRepository,
            WorkflowTransitionRepository workflowTransitionRepository,
            AppUserRepository appUserRepository,
            AuditService auditService,
            StatusHistoryService statusHistoryService) {
        this.ticketRepository = ticketRepository;
        this.workflowStateRepository = workflowStateRepository;
        this.workflowTransitionRepository = workflowTransitionRepository;
        this.appUserRepository = appUserRepository;
        this.auditService = auditService;
        this.statusHistoryService = statusHistoryService;
    }

    @Transactional
    public TicketDetailResponse transition(String ticketKey, String toStateKey, String comment,
            AuthPrincipal principal) {
        // Transition comments belong to Phase 4, where CommentService can persist
        // the comment and its audit entry in this same transaction. Reject them for
        // now instead of accepting user input and silently discarding it.
        if (comment != null && !comment.isBlank()) {
            throw ApiException.validation("Transition comments are not available until Phase 4.");
        }

        Ticket ticket = findVisibleTicket(ticketKey, principal);
        AppUser actor = appUserRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("Current user no longer exists."));

        Long workflowId = ticket.getTicketType().getWorkflow().getId();
        WorkflowState fromState = ticket.getCurrentState();
        WorkflowState toState = workflowStateRepository.findByWorkflowIdAndKey(workflowId, toStateKey)
                .orElseThrow(() -> illegalTransition(ticketKey, fromState.getKey(), toStateKey));
        WorkflowTransition transition = workflowTransitionRepository
                .findByWorkflowIdAndFromStateIdAndToStateId(workflowId, fromState.getId(), toState.getId())
                .orElseThrow(() -> illegalTransition(ticketKey, fromState.getKey(), toStateKey));

        if (!isAllowed(transition, principal)) {
            throw illegalTransition(ticketKey, fromState.getKey(), toStateKey);
        }

        ticket.setCurrentState(toState);
        if (transition.getResponsibilityAfter() != null) {
            ticket.setCurrentResponsibility(transition.getResponsibilityAfter());
        }
        Ticket saved = ticketRepository.save(ticket);

        statusHistoryService.record(saved, fromState, toState, actor.getId());
        auditService.record(saved, actor.getId(), AuditAction.STATUS_CHANGED,
                "status", fromState.getKey(), toState.getKey());

        return TicketDetailResponse.from(saved, allowedTransitions(saved, principal));
    }

    @Transactional(readOnly = true)
    public List<String> allowedTransitions(Ticket ticket, AuthPrincipal principal) {
        Long workflowId = ticket.getTicketType().getWorkflow().getId();
        Long fromStateId = ticket.getCurrentState().getId();
        return workflowTransitionRepository.findByWorkflowIdAndFromStateId(workflowId, fromStateId).stream()
                .filter(transition -> isAllowed(transition, principal))
                .map(transition -> transition.getToState().getKey())
                .toList();
    }

    private boolean isAllowed(WorkflowTransition transition, AuthPrincipal principal) {
        if (!principal.hasPermission(transition.getRequiredPermission().getKey())) {
            return false;
        }
        Responsibility requiredParty = transition.getRequiredParty();
        return requiredParty == null || requiredParty == principal.party();
    }

    private Ticket findVisibleTicket(String ticketKey, AuthPrincipal principal) {
        if (principal.party() == Responsibility.CLIENT) {
            Long organizationId = principal.organizationId();
            return ticketRepository.findByTicketKeyAndOrganizationId(ticketKey, organizationId)
                    .orElseThrow(() -> ApiException.notFound("Ticket not found: " + ticketKey));
        }
        return ticketRepository.findByTicketKey(ticketKey)
                .orElseThrow(() -> ApiException.notFound("Ticket not found: " + ticketKey));
    }

    private IllegalTransitionException illegalTransition(String ticketKey, String fromState, String toState) {
        return new IllegalTransitionException(
                "Cannot move ticket %s from %s to %s.".formatted(ticketKey, fromState, toState));
    }
}
