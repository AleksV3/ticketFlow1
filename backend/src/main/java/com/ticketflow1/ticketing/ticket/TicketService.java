package com.ticketflow1.ticketing.ticket;

import com.ticketflow1.ticketing.audit.AuditAction;
import com.ticketflow1.ticketing.audit.AuditService;
import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.common.PagedResponse;
import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.organization.OrganizationRepository;
import com.ticketflow1.ticketing.proposal.ProposalDetailService;
import com.ticketflow1.ticketing.sla.SlaCalculator;
import com.ticketflow1.ticketing.sla.SlaStatus;
import com.ticketflow1.ticketing.sla.SlaStatusService;
import com.ticketflow1.ticketing.sla.SlaSpecifications;
import com.ticketflow1.ticketing.statushistory.StatusHistoryService;
import com.ticketflow1.ticketing.ticket.dto.CreateTicketRequest;
import com.ticketflow1.ticketing.ticket.dto.TicketDetailResponse;
import com.ticketflow1.ticketing.ticket.dto.TicketSummaryResponse;
import com.ticketflow1.ticketing.ticket.dto.UpdateTicketRequest;
import com.ticketflow1.ticketing.user.AppUser;
import com.ticketflow1.ticketing.user.AppUserRepository;
import com.ticketflow1.ticketing.workflow.TicketType;
import com.ticketflow1.ticketing.workflow.TicketTypeRepository;
import com.ticketflow1.ticketing.workflow.TicketTransitionService;
import com.ticketflow1.ticketing.workflow.WorkflowState;
import com.ticketflow1.ticketing.workflow.WorkflowStateRepository;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.time.Clock;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns ticket creation, update, listing, and visibility rules.
 *
 * This service is the main ticket domain coordinator. It resolves the current
 * actor, enforces tenancy and permission checks, applies SLA deadlines,
 * validates workflow-specific constraints, and returns enriched ticket detail
 * views that include allowed transitions and proposal/SLA state.
 */
@Service
public class TicketService {

    private static final String DEFECT_TYPE_KEY = "DEFECT";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final TicketRepository ticketRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final WorkflowStateRepository workflowStateRepository;
    private final AppUserRepository appUserRepository;
    private final OrganizationRepository organizationRepository;
    private final TicketKeyGenerator ticketKeyGenerator;
    private final AuditService auditService;
    private final StatusHistoryService statusHistoryService;
    private final TicketTransitionService ticketTransitionService;
    private final ProposalDetailService proposalDetailService;
    private final SlaCalculator slaCalculator;
    private final SlaStatusService slaStatusService;
    private final Clock clock;

    public TicketService(TicketRepository ticketRepository,
            TicketTypeRepository ticketTypeRepository,
            WorkflowStateRepository workflowStateRepository,
            AppUserRepository appUserRepository,
            OrganizationRepository organizationRepository,
            TicketKeyGenerator ticketKeyGenerator,
            AuditService auditService,
            StatusHistoryService statusHistoryService,
            TicketTransitionService ticketTransitionService,
            ProposalDetailService proposalDetailService,
            SlaCalculator slaCalculator,
            SlaStatusService slaStatusService,
            Clock clock) {
        this.ticketRepository = ticketRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.workflowStateRepository = workflowStateRepository;
        this.appUserRepository = appUserRepository;
        this.organizationRepository = organizationRepository;
        this.ticketKeyGenerator = ticketKeyGenerator;
        this.auditService = auditService;
        this.statusHistoryService = statusHistoryService;
        this.ticketTransitionService = ticketTransitionService;
        this.proposalDetailService = proposalDetailService;
        this.slaCalculator = slaCalculator;
        this.slaStatusService = slaStatusService;
        this.clock = clock;
    }

    /**
     * Creates a ticket, assigns its initial workflow state, and persists the
     * first audit and status-history entries.
     */
    @Transactional
    public TicketDetailResponse createTicket(CreateTicketRequest request, AuthPrincipal principal) {
        AppUser actor = appUserRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("Current user no longer exists."));

        Organization organization = resolveOrganization(actor, principal, request.organizationId());
        TicketType ticketType = ticketTypeRepository.findByOrganizationIdAndKey(organization.getId(), request.type())
                .orElseThrow(() -> ApiException.validation(
                        "Ticket type '%s' is not configured for organization %d."
                                .formatted(request.type(), organization.getId())));
        WorkflowState initialState = workflowStateRepository
                .findByWorkflowIdAndInitialTrue(ticketType.getWorkflow().getId())
                .orElseThrow(() -> ApiException.validation(
                        "Workflow '%s' has no initial state.".formatted(ticketType.getWorkflow().getName())));

        validateSeverityRules(ticketType, request);

        Ticket ticket = new Ticket(
                ticketKeyGenerator.nextKey(),
                ticketType,
                initialState,
                request.priority(),
                request.severity(),
                request.title().trim(),
                request.description().trim(),
                organization,
                actor,
                Responsibility.TICKETFLOW1);

        if (request.ticketLeadId() != null || request.developerIds() != null) {
            requireAssignmentPermission(principal);
            if (request.ticketLeadId() != null) {
                AppUser lead = internalUser(request.ticketLeadId(), "ticketLeadId");
                ticket.setTicketLead(lead);
            }
            if (request.developerIds() != null) {
                Set<AppUser> developers = new LinkedHashSet<>(appUserRepository.findAllById(request.developerIds()));
                if (developers.size() != request.developerIds().size()
                        || developers.stream().anyMatch(user -> user.getParty() != Responsibility.TICKETFLOW1)) {
                    throw ApiException.validation("Developers must be valid TicketFlow1 users.");
                }
                ticket.replaceDevelopers(developers);
            }
        }

        Ticket saved = ticketRepository.saveAndFlush(ticket);
        if (DEFECT_TYPE_KEY.equals(ticketType.getKey())) {
            applyDeadlines(saved, saved.getSeverity(), saved.getCreatedAt());
            saved = ticketRepository.saveAndFlush(saved);
        }
        auditService.record(saved, actor.getId(), AuditAction.TICKET_CREATED);
        statusHistoryService.record(saved, null, initialState, actor.getId());
        return detail(saved, principal);
    }

    /**
     * Loads a ticket in the current user's scope and returns the full detail view.
     */
    @Transactional(readOnly = true)
    public TicketDetailResponse getTicket(String ticketKey, AuthPrincipal principal) {
        Ticket ticket = findVisibleTicket(ticketKey, principal);
        return detail(ticket, principal);
    }

    /**
     * Applies editable ticket changes while enforcing field-specific rules.
     */
    @Transactional
    public TicketDetailResponse updateTicket(String ticketKey, UpdateTicketRequest request, AuthPrincipal principal) {
        Ticket ticket = findVisibleTicket(ticketKey, principal);
        AppUser actor = appUserRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("Current user no longer exists."));

        if (request.status() != null) {
            throw ApiException.validation("status must be changed via /transition.");
        }
        boolean changesContent = request.title() != null || request.description() != null
                || request.priority() != null || request.severity() != null || request.assignedTeam() != null;
        if (changesContent && !principal.hasPermission("TICKET_UPDATE")) {
            throw ApiException.forbidden("TICKET_UPDATE permission is required.");
        }

        boolean changed = false;

        if (request.title() != null) {
            String title = request.title().trim();
            if (title.isEmpty()) {
                throw ApiException.validation("title must not be blank.");
            }
            if (!title.equals(ticket.getTitle())) {
                auditService.record(ticket, actor.getId(), AuditAction.TICKET_UPDATED,
                        "title", ticket.getTitle(), title);
                ticket.setTitle(title);
                changed = true;
            }
        }

        if (request.description() != null) {
            String description = request.description().trim();
            if (description.isEmpty()) {
                throw ApiException.validation("description must not be blank.");
            }
            if (!description.equals(ticket.getDescription())) {
                auditService.record(ticket, actor.getId(), AuditAction.TICKET_UPDATED,
                        "description", ticket.getDescription(), description);
                ticket.setDescription(description);
                changed = true;
            }
        }

        if (request.priority() != null) {
            requireTicketflow1Party(principal, "priority");
            if (ticket.getPriority() != request.priority()) {
                auditService.record(ticket, actor.getId(), AuditAction.PRIORITY_CHANGED,
                        "priority", ticket.getPriority().name(), request.priority().name());
                ticket.setPriority(request.priority());
                changed = true;
            }
        }

        if (request.severity() != null) {
            requireTicketflow1Party(principal, "severity");
            if (!DEFECT_TYPE_KEY.equals(ticket.getTicketType().getKey())) {
                throw ApiException.validation("severity is allowed only when type is DEFECT.");
            }
            if (!"ANALYSIS".equals(ticket.getCurrentState().getKey())) {
                throw ApiException.validation("severity may be changed only while a Defect is in ANALYSIS.");
            }
            if (ticket.getSeverity() != request.severity()) {
                auditService.record(ticket, actor.getId(), AuditAction.SEVERITY_CHANGED,
                        "severity", ticket.getSeverity().name(), request.severity().name());
                ticket.setSeverity(request.severity());
                applyDeadlines(ticket, request.severity(), clock.instant());
                changed = true;
            }
        }

        if (request.ticketLeadId() != null) {
            requireAssignmentPermission(principal);
            requireTicketflow1Party(principal, "ticketLeadId");
            AppUser ticketLead = appUserRepository.findById(request.ticketLeadId())
                    .orElseThrow(() -> ApiException.validation("ticketLeadId not found: " + request.ticketLeadId()));
            if (ticketLead.getParty() != Responsibility.TICKETFLOW1) {
                throw ApiException.validation("ticketLeadId must belong to a TICKETFLOW1-party user.");
            }
            Long currentLeadId = ticket.getTicketLead() == null ? null : ticket.getTicketLead().getId();
            if (!request.ticketLeadId().equals(currentLeadId)) {
                auditService.record(ticket, actor.getId(), AuditAction.ASSIGNEE_CHANGED,
                        "ticketLeadId",
                        ticket.getTicketLead() == null ? null : ticket.getTicketLead().getDisplayName(),
                        ticketLead.getDisplayName());
                ticket.setTicketLead(ticketLead);
                changed = true;
            }
        }

        if (request.developerIds() != null) {
            requireAssignmentPermission(principal);
            Set<AppUser> developers = new LinkedHashSet<>(appUserRepository.findAllById(request.developerIds()));
            if (developers.size() != request.developerIds().size()) {
                throw ApiException.validation("One or more developerIds do not exist.");
            }
            if (developers.stream().anyMatch(user -> user.getParty() != Responsibility.TICKETFLOW1)) {
                throw ApiException.validation("Developers must be TicketFlow1 users.");
            }
            ticket.replaceDevelopers(developers);
            auditService.record(ticket, actor.getId(), AuditAction.ASSIGNEE_CHANGED,
                    "developers", null, developers.stream().map(AppUser::getDisplayName).sorted().toList().toString());
            changed = true;
        }

        if (request.assignedTeam() != null) {
            requireTicketflow1Party(principal, "assignedTeam");
            String assignedTeam = request.assignedTeam().trim();
            if (assignedTeam.isEmpty()) {
                assignedTeam = null;
            }
            String currentAssignedTeam = ticket.getAssignedTeam();
            if ((assignedTeam == null && currentAssignedTeam != null)
                    || (assignedTeam != null && !assignedTeam.equals(currentAssignedTeam))) {
                auditService.record(ticket, actor.getId(), AuditAction.TICKET_UPDATED,
                        "assignedTeam", currentAssignedTeam, assignedTeam);
                ticket.setAssignedTeam(assignedTeam);
                changed = true;
            }
        }

        Ticket saved = changed ? ticketRepository.save(ticket) : ticket;
        return detail(saved, principal);
    }

    private void requireAssignmentPermission(AuthPrincipal principal) {
        requireTicketflow1Party(principal, "ticket team");
        if (!principal.hasPermission("TICKET_ASSIGN")) {
            throw ApiException.forbidden("TICKET_ASSIGN permission is required.");
        }
    }

    private AppUser internalUser(Long userId, String fieldName) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> ApiException.validation(fieldName + " not found: " + userId));
        if (user.getParty() != Responsibility.TICKETFLOW1) {
            throw ApiException.validation(fieldName + " must belong to a TicketFlow1 user.");
        }
        return user;
    }

    /**
     * Lists tickets using the active tenant scope and optional search filters.
     */
    @Transactional(readOnly = true)
    public PagedResponse<TicketSummaryResponse> listTickets(String type, String status, String lifecycle, Severity severity,
            Priority priority, String assignedTo, Responsibility responsibility, String slaStatus,
            Long organizationId, String q, int page, int pageSize, AuthPrincipal principal) {
        SlaStatus requestedSlaStatus = parseSlaStatus(slaStatus);

        int size = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int pageNumber = Math.max(page, 0);
        Pageable pageable = PageRequest.of(pageNumber, size, Sort.by("updatedAt").descending());

        Specification<Ticket> spec = Specification.where(null);
        if (principal.party() == Responsibility.CLIENT) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("organization").get("id"), principal.organizationId()));
        } else if (organizationId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("organization").get("id"), organizationId));
        }
        if (type != null && !type.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("ticketType").get("key"), type));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("currentState").get("key"), status));
        }
        if (lifecycle != null && !lifecycle.isBlank()) {
            if (!"active".equalsIgnoreCase(lifecycle) && !"closed".equalsIgnoreCase(lifecycle)) {
                throw ApiException.validation("lifecycle must be 'active' or 'closed'.");
            }
            boolean terminal = "closed".equalsIgnoreCase(lifecycle);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("currentState").get("terminal"), terminal));
        }
        if (severity != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("severity"), severity));
        }
        if (priority != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("priority"), priority));
        }
        if (responsibility != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("currentResponsibility"), responsibility));
        }
        if ("me".equals(assignedTo)) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("ticketLead").get("id"), principal.userId()));
        } else if ("unassigned".equals(assignedTo)) {
            spec = spec.and((root, query, cb) -> cb.isNull(root.get("ticketLead")));
        } else if (assignedTo != null && !assignedTo.isBlank()) {
            throw ApiException.validation("assignedTo must be 'me' or 'unassigned'.");
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("description")), like),
                    cb.like(cb.lower(root.get("ticketKey")), like)));
        }
        if (requestedSlaStatus != null) {
            spec = spec.and(SlaSpecifications.hasStatus(requestedSlaStatus, clock.instant(), slaCalculator));
        }

        return PagedResponse.from(ticketRepository.findAll(spec, pageable),
                ticket -> TicketSummaryResponse.from(ticket, slaStatusService.status(ticket)));
    }

    /**
     * Resolves a ticket by key while honoring organization visibility.
     */
    @Transactional(readOnly = true)
    public Ticket findVisibleTicket(String ticketKey, AuthPrincipal principal) {
        if (principal.party() == Responsibility.CLIENT) {
            Long organizationId = principal.organizationId();
            return ticketRepository.findByTicketKeyAndOrganizationId(ticketKey, organizationId)
                    .orElseThrow(() -> ApiException.notFound("Ticket not found: " + ticketKey));
        }
        return ticketRepository.findByTicketKey(ticketKey)
                .orElseThrow(() -> ApiException.notFound("Ticket not found: " + ticketKey));
    }

    private TicketDetailResponse detail(Ticket ticket, AuthPrincipal principal) {
        return TicketDetailResponse.from(ticket, ticketTransitionService.allowedTransitions(ticket, principal),
                proposalDetailService.detail(ticket, principal), slaStatusService.status(ticket));
    }

    private void applyDeadlines(Ticket ticket, Severity severity, java.time.Instant updateBase) {
        SlaCalculator.SlaDeadlines deadlines = slaCalculator.calculate(severity, ticket.getCreatedAt(), updateBase);
        ticket.setResponseDueAt(deadlines.responseDueAt());
        ticket.setFirstInfoDueAt(deadlines.firstInfoDueAt());
        ticket.setNextUpdateDueAt(deadlines.nextUpdateDueAt());
    }

    private SlaStatus parseSlaStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return SlaStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw ApiException.validation("slaStatus must be OK, DUE_SOON, BREACHED, or NOT_APPLICABLE.");
        }
    }

    private Organization resolveOrganization(AppUser actor, AuthPrincipal principal, Long organizationId) {
        if (principal.party() == Responsibility.CLIENT) {
            Organization actorOrganization = actor.getOrganization();
            if (actorOrganization == null) {
                throw ApiException.validation("CLIENT users must belong to an organization.");
            }
            return actorOrganization;
        }
        if (organizationId == null) {
            throw ApiException.validation("organizationId is required for TICKETFLOW1-party ticket creation.");
        }
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> ApiException.validation("Organization not found: " + organizationId));
    }

    private void validateSeverityRules(TicketType ticketType, CreateTicketRequest request) {
        boolean isDefect = DEFECT_TYPE_KEY.equals(ticketType.getKey());
        if (isDefect && request.severity() == null) {
            throw ApiException.validation("severity is required when type is DEFECT.");
        }
        if (!isDefect && request.severity() != null) {
            throw ApiException.validation("severity is allowed only when type is DEFECT.");
        }
    }

    private void requireTicketflow1Party(AuthPrincipal principal, String fieldName) {
        if (principal.party() != Responsibility.TICKETFLOW1) {
            throw ApiException.forbidden("Only TICKETFLOW1-party users may change " + fieldName + ".");
        }
    }
}
