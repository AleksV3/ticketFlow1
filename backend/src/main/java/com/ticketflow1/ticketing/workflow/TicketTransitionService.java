package com.ticketflow1.ticketing.workflow;

import com.ticketflow1.ticketing.audit.AuditAction;
import com.ticketflow1.ticketing.audit.AuditService;
import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.comment.CommentService;
import com.ticketflow1.ticketing.comment.CommentVisibility;
import com.ticketflow1.ticketing.proposal.ProposalDetailService;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.common.IllegalTransitionException;
import com.ticketflow1.ticketing.statushistory.StatusHistoryService;
import com.ticketflow1.ticketing.sla.SlaStatusService;
import com.ticketflow1.ticketing.ticket.Responsibility;
import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.ticket.TicketRepository;
import com.ticketflow1.ticketing.ticket.dto.TicketDetailResponse;
import com.ticketflow1.ticketing.ticketconfig.TicketApproval;
import com.ticketflow1.ticketing.ticketconfig.TicketApprovalRepository;
import com.ticketflow1.ticketing.ticketconfig.TicketApprovalStatus;
import com.ticketflow1.ticketing.ticketconfig.DecisionKind;
import com.ticketflow1.ticketing.ticketconfig.DecisionValue;
import com.ticketflow1.ticketing.ticketconfig.TicketDecision;
import com.ticketflow1.ticketing.ticketconfig.TicketDecisionRepository;
import com.ticketflow1.ticketing.user.AppUser;
import com.ticketflow1.ticketing.user.AppUserRepository;
import java.util.List;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces workflow state transitions for tickets.
 *
 * The service is responsible for deciding which transitions are legal for the
 * current actor, applying the state change, recording status history, and
 * exposing the set of next STANDARD transitions for the UI.
 */
@Service
public class TicketTransitionService {

    private final TicketRepository ticketRepository;
    private final WorkflowStateRepository workflowStateRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;
    private final StatusHistoryService statusHistoryService;
    private final CommentService commentService;
    private final ProposalDetailService proposalDetailService;
    private final SlaStatusService slaStatusService;
    private final Clock clock;
    private final TicketApprovalRepository ticketApprovalRepository;
    private final TicketDecisionRepository ticketDecisionRepository;

    public TicketTransitionService(TicketRepository ticketRepository,
            WorkflowStateRepository workflowStateRepository,
            WorkflowTransitionRepository workflowTransitionRepository,
            AppUserRepository appUserRepository,
            AuditService auditService,
            StatusHistoryService statusHistoryService,
            CommentService commentService,
            ProposalDetailService proposalDetailService,
            SlaStatusService slaStatusService,
            TicketApprovalRepository ticketApprovalRepository,
            TicketDecisionRepository ticketDecisionRepository,
            Clock clock) {
        this.ticketRepository = ticketRepository;
        this.workflowStateRepository = workflowStateRepository;
        this.workflowTransitionRepository = workflowTransitionRepository;
        this.appUserRepository = appUserRepository;
        this.auditService = auditService;
        this.statusHistoryService = statusHistoryService;
        this.commentService = commentService;
        this.proposalDetailService = proposalDetailService;
        this.slaStatusService = slaStatusService;
        this.ticketApprovalRepository = ticketApprovalRepository;
        this.ticketDecisionRepository = ticketDecisionRepository;
        this.clock = clock;
    }

    /**
     * Moves a ticket to a named state and appends an optional public comment.
     */
    @Transactional
    public TicketDetailResponse transition(String ticketKey, String toStateKey, String comment,
            AuthPrincipal principal) {
        Ticket ticket = findVisibleTicket(ticketKey, principal);
        Ticket saved = transitionToState(ticket, toStateKey, TransitionOperationKind.STANDARD, principal);
        if (comment != null && !comment.isBlank()) {
            commentService.createForTicket(saved, comment, CommentVisibility.PUBLIC, principal);
        }
        return TicketDetailResponse.from(saved, allowedTransitions(saved, principal),
                proposalDetailService.detail(saved, principal), slaStatusService.status(saved));
    }

    @Transactional
    public TicketDetailResponse correctionReturn(String ticketKey, String reason, AuthPrincipal principal) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 10000) {
            throw ApiException.validation("A correction reason is required (maximum 10000 characters).");
        }
        Ticket ticket = findVisibleTicket(ticketKey, principal);
        AppUser actor = appUserRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("Current user no longer exists."));
        Ticket saved = transitionOwned(ticket, TransitionOperationKind.CORRECTION_RETURN, principal);
        commentService.createForTicket(saved, reason.trim(), CommentVisibility.INTERNAL, principal);
        auditService.record(saved, actor.getId(), AuditAction.CORRECTION_RETURN,
                "reason", null, "recorded");
        return TicketDetailResponse.from(saved, allowedTransitions(saved, principal),
                proposalDetailService.detail(saved, principal), slaStatusService.status(saved));
    }

    @Transactional
    public TicketDetailResponse protectedDecision(String ticketKey, TransitionOperationKind operation,
            String reason, AuthPrincipal principal) {
        boolean rejection = operation == TransitionOperationKind.WORKFLOW_REJECT
                || operation == TransitionOperationKind.CLIENT_REJECT;
        if (rejection && (reason == null || reason.trim().length() < 2)) {
            throw ApiException.validation("A rejection reason of at least 2 characters is required.");
        }
        Ticket ticket = findVisibleTicket(ticketKey, principal);
        TicketApproval approval = ticketApprovalRepository.findForUpdate(
                        ticket.getId(), TicketApprovalStatus.PENDING)
                .orElseThrow(() -> ApiException.conflict(
                        "This ticket has no active pending approval."));
        WorkflowTransition edge = ownedTransition(ticket, operation);
        if (!isAllowed(approval, ticket, edge, principal)) {
            throw ApiException.forbidden("You are not authorized to decide this ticket approval.");
        }
        AppUser actor = appUserRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("Current user no longer exists."));
        WorkflowState fromState = ticket.getCurrentState();
        long observedVersion = ticket.getVersion();
        Ticket saved = apply(ticket, edge, principal);
        boolean approved = operation == TransitionOperationKind.WORKFLOW_APPROVE;
        TicketApprovalStatus approvalStatus = approved
                ? TicketApprovalStatus.APPROVED : TicketApprovalStatus.REJECTED;
        approval.decide(approvalStatus, actor, clock.instant());
        ticketApprovalRepository.save(approval);
        ticketDecisionRepository.save(new TicketDecision(
                saved,
                DecisionKind.WORKFLOW_APPROVAL,
                approved ? DecisionValue.APPROVED : DecisionValue.REJECTED,
                actor,
                fromState,
                saved.getCurrentState(),
                reason == null ? null : reason.trim(),
                observedVersion));
        auditService.record(saved, actor.getId(),
                approved ? AuditAction.WORKFLOW_APPROVED : AuditAction.WORKFLOW_REJECTED,
                "approvalDecision", "PENDING", approvalStatus.name());
        if (reason != null && !reason.isBlank()) {
            CommentVisibility visibility = principal.party() == Responsibility.CLIENT
                    ? CommentVisibility.PUBLIC : CommentVisibility.INTERNAL;
            commentService.createForTicket(saved, reason.trim(), visibility, principal);
        }
        return TicketDetailResponse.from(saved, allowedTransitions(saved, principal),
                proposalDetailService.detail(saved, principal), slaStatusService.status(saved));
    }

    /**
     * Applies the single owned transition that matches the requested operation.
     */
    @Transactional
    public Ticket transitionOwned(Ticket ticket, TransitionOperationKind operationKind, AuthPrincipal principal) {
        List<WorkflowTransition> matches = workflowTransitionRepository
                .findByWorkflowIdAndFromStateId(ticket.getTicketType().getWorkflow().getId(),
                        ticket.getCurrentState().getId()).stream()
                .filter(item -> item.getOperationKind() == operationKind)
                .toList();
        if (matches.size() != 1) return applyMissingOwnedTransition(ticket, operationKind);
        if (!isAllowed(ticket, matches.getFirst(), principal)) {
            throw ApiException.forbidden("You are not authorized to decide this ticket approval.");
        }
        return apply(ticket, matches.getFirst(), principal);
    }

    private WorkflowTransition ownedTransition(Ticket ticket, TransitionOperationKind operationKind) {
        List<WorkflowTransition> matches = workflowTransitionRepository
                .findByWorkflowIdAndFromStateId(ticket.getTicketType().getWorkflow().getId(),
                        ticket.getCurrentState().getId()).stream()
                .filter(item -> item.getOperationKind() == operationKind)
                .toList();
        if (matches.size() != 1) {
            throw new com.ticketflow1.ticketing.common.InvalidStateException(
                    "No " + operationKind + " operation is available from "
                            + ticket.getCurrentState().getKey() + ".");
        }
        return matches.getFirst();
    }

    private Ticket applyMissingOwnedTransition(Ticket ticket, TransitionOperationKind operationKind) {
        throw new com.ticketflow1.ticketing.common.InvalidStateException(
                "No " + operationKind + " operation is available from "
                        + ticket.getCurrentState().getKey() + ".");
    }

    /**
     * Validates the requested destination state against the configured workflow graph.
     */
    private Ticket transitionToState(Ticket ticket, String toStateKey, TransitionOperationKind operationKind,
            AuthPrincipal principal) {
        Long workflowId = ticket.getTicketType().getWorkflow().getId();
        WorkflowState fromState = ticket.getCurrentState();
        WorkflowState toState = workflowStateRepository.findByWorkflowIdAndKey(workflowId, toStateKey)
                .orElseThrow(() -> illegalTransition(ticket.getTicketKey(), fromState.getKey(), toStateKey));
        WorkflowTransition transition = workflowTransitionRepository
                .findByWorkflowIdAndFromStateIdAndToStateId(workflowId, fromState.getId(), toState.getId())
                .orElseThrow(() -> illegalTransition(ticket.getTicketKey(), fromState.getKey(), toStateKey));
        if (transition.getOperationKind() != operationKind || !isAllowed(ticket, transition, principal)) {
            throw illegalTransition(ticket.getTicketKey(), fromState.getKey(), toStateKey);
        }

        return apply(ticket, transition, principal);
    }

    private Ticket apply(Ticket ticket, WorkflowTransition transition, AuthPrincipal principal) {
        AppUser actor = appUserRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("Current user no longer exists."));
        WorkflowState fromState = ticket.getCurrentState();
        WorkflowState toState = transition.getToState();
        if (!isAllowed(ticket, transition, principal)) {
            throw illegalTransition(ticket.getTicketKey(), fromState.getKey(), toState.getKey());
        }

        ticket.setCurrentState(toState);
        if (ticket.getTicketType().getCapability() == TicketTypeCapability.DEFECT_SLA
                && "REPORTED".equals(fromState.getKey())
                && "ANALYSIS".equals(toState.getKey())
                && ticket.getRespondedAt() == null) {
            ticket.setRespondedAt(clock.instant());
        }
        if (transition.getResponsibilityAfter() != null) {
            ticket.setCurrentResponsibility(transition.getResponsibilityAfter());
        }
        Ticket saved = ticketRepository.save(ticket);

        createPendingApprovalIfRequired(saved, toState);

        statusHistoryService.record(saved, fromState, toState, actor.getId());
        auditService.record(saved, actor.getId(), AuditAction.STATUS_CHANGED,
                "status", fromState.getKey(), toState.getKey());
        return saved;
    }

    /**
     * Returns the STANDARD transition targets the current actor may choose from.
     */
    @Transactional(readOnly = true)
    public List<String> allowedTransitions(Ticket ticket, AuthPrincipal principal) {
        Long workflowId = ticket.getTicketType().getWorkflow().getId();
        Long fromStateId = ticket.getCurrentState().getId();
        return workflowTransitionRepository.findByWorkflowIdAndFromStateId(workflowId, fromStateId).stream()
                .filter(transition -> isAllowed(ticket, transition, principal))
                .filter(transition -> transition.getOperationKind() == TransitionOperationKind.STANDARD)
                .map(transition -> transition.getToState().getKey())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> workflowCommands(Ticket ticket, AuthPrincipal principal) {
        Long workflowId = ticket.getTicketType().getWorkflow().getId();
        Long fromStateId = ticket.getCurrentState().getId();
        return workflowTransitionRepository.findByWorkflowIdAndFromStateId(workflowId, fromStateId).stream()
                .filter(transition -> transition.getOperationKind() != TransitionOperationKind.STANDARD)
                .filter(transition -> isAllowed(ticket, transition, principal))
                .map(transition -> transition.getOperationKind().name())
                .distinct()
                .toList();
    }

    private boolean isAllowed(Ticket ticket, WorkflowTransition transition, AuthPrincipal principal) {
        if (!principal.hasPermission(transition.getRequiredPermission().getKey())) {
            return false;
        }
        Responsibility requiredParty = transition.getRequiredParty();
        if (requiredParty != null && requiredParty != principal.party()) return false;
        TransitionOperationKind operation = transition.getOperationKind();
        if (operation == TransitionOperationKind.WORKFLOW_APPROVE
                || operation == TransitionOperationKind.WORKFLOW_REJECT) {
            if (principal.party() != Responsibility.TICKETFLOW1) {
                return false;
            }
            return pendingApproval(ticket)
                    .map(approval -> isNormalWorkflowApprover(approval, principal))
                    .orElse(false);
        }
        if (operation == TransitionOperationKind.CLIENT_ACCEPT
                || operation == TransitionOperationKind.CLIENT_REJECT) {
            return principal.party() == Responsibility.CLIENT
                    && ticket.getBusinessOwner() != null
                    && ticket.getBusinessOwner().getId().equals(principal.userId())
                    && ticket.getOrganization().getId().equals(principal.organizationId());
        }
        return true;
    }

    private void createPendingApprovalIfRequired(Ticket ticket, WorkflowState enteredState) {
        List<WorkflowTransition> outgoing = workflowTransitionRepository.findByWorkflowIdAndFromStateId(
                ticket.getTicketType().getWorkflow().getId(), enteredState.getId());
        boolean approvalState = outgoing.stream().anyMatch(edge ->
                edge.getOperationKind() == TransitionOperationKind.WORKFLOW_APPROVE
                        || edge.getOperationKind() == TransitionOperationKind.WORKFLOW_REJECT);
        if (!approvalState || pendingApproval(ticket).isPresent()) {
            return;
        }

        var assignedTeam = ticket.getRoutingRule() == null
                ? (ticket.getTeams().size() == 1 ? ticket.getTeams().iterator().next() : null)
                : ticket.getRoutingRule().getTeam();
        if (ticket.getResolvedApprover() == null && assignedTeam == null) {
            throw ApiException.conflict("The approval state has no configured approver or assigned team.");
        }
        ticketApprovalRepository.save(new TicketApproval(
                ticket, enteredState, ticket.getResolvedApprover(), assignedTeam));
    }

    private java.util.Optional<TicketApproval> pendingApproval(Ticket ticket) {
        if (ticket.getId() == null) {
            return java.util.Optional.empty();
        }
        return ticketApprovalRepository.findByTicketIdAndStatus(
                ticket.getId(), TicketApprovalStatus.PENDING);
    }

    private boolean isNormalWorkflowApprover(TicketApproval approval, AuthPrincipal principal) {
        if (approval.getAssignedApprover() != null) {
            return approval.getAssignedApprover().isActive()
                    && approval.getAssignedApprover().getId().equals(principal.userId());
        }
        return approval.getAssignedTeam() != null
                && approval.getAssignedTeam().getLeader() != null
                && approval.getAssignedTeam().getLeader().isActive()
                && approval.getAssignedTeam().getLeader().getId().equals(principal.userId());
    }

    private boolean isAllowed(TicketApproval approval, Ticket ticket,
            WorkflowTransition transition, AuthPrincipal principal) {
        if (!principal.hasPermission(transition.getRequiredPermission().getKey())) {
            return false;
        }
        if (transition.getRequiredParty() != null
                && transition.getRequiredParty() != principal.party()) {
            return false;
        }
        return approval.getTicket().getId().equals(ticket.getId())
                && approval.getPendingState().getId().equals(ticket.getCurrentState().getId())
                && principal.party() == Responsibility.TICKETFLOW1
                && isNormalWorkflowApprover(approval, principal);
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
