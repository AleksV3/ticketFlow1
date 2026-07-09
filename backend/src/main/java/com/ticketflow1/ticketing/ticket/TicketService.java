package com.ticketflow1.ticketing.ticket;

import com.ticketflow1.ticketing.audit.AuditAction;
import com.ticketflow1.ticketing.audit.AuditService;
import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.organization.OrganizationRepository;
import com.ticketflow1.ticketing.statushistory.StatusHistoryService;
import com.ticketflow1.ticketing.ticket.dto.CreateTicketRequest;
import com.ticketflow1.ticketing.ticket.dto.TicketDetailResponse;
import com.ticketflow1.ticketing.user.AppUser;
import com.ticketflow1.ticketing.user.AppUserRepository;
import com.ticketflow1.ticketing.workflow.TicketType;
import com.ticketflow1.ticketing.workflow.TicketTypeRepository;
import com.ticketflow1.ticketing.workflow.WorkflowState;
import com.ticketflow1.ticketing.workflow.WorkflowStateRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private static final String DEFECT_TYPE_KEY = "DEFECT";

    private final TicketRepository ticketRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final WorkflowStateRepository workflowStateRepository;
    private final AppUserRepository appUserRepository;
    private final OrganizationRepository organizationRepository;
    private final TicketKeyGenerator ticketKeyGenerator;
    private final AuditService auditService;
    private final StatusHistoryService statusHistoryService;

    public TicketService(TicketRepository ticketRepository,
            TicketTypeRepository ticketTypeRepository,
            WorkflowStateRepository workflowStateRepository,
            AppUserRepository appUserRepository,
            OrganizationRepository organizationRepository,
            TicketKeyGenerator ticketKeyGenerator,
            AuditService auditService,
            StatusHistoryService statusHistoryService) {
        this.ticketRepository = ticketRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.workflowStateRepository = workflowStateRepository;
        this.appUserRepository = appUserRepository;
        this.organizationRepository = organizationRepository;
        this.ticketKeyGenerator = ticketKeyGenerator;
        this.auditService = auditService;
        this.statusHistoryService = statusHistoryService;
    }

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

        Ticket saved = ticketRepository.saveAndFlush(ticket);
        auditService.record(saved, actor.getId(), AuditAction.TICKET_CREATED);
        statusHistoryService.record(saved, null, initialState, actor.getId());
        return TicketDetailResponse.from(saved, List.of());
    }

    @Transactional(readOnly = true)
    public TicketDetailResponse getTicket(String ticketKey, AuthPrincipal principal) {
        return TicketDetailResponse.from(findVisibleTicket(ticketKey, principal), List.of());
    }

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
}
