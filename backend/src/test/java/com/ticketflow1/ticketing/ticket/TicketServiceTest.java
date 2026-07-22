package com.ticketflow1.ticketing.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketflow1.ticketing.audit.AuditAction;
import com.ticketflow1.ticketing.audit.AuditService;
import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.organization.OrganizationRepository;
import com.ticketflow1.ticketing.proposal.ProposalDetailService;
import com.ticketflow1.ticketing.rbac.Role;
import com.ticketflow1.ticketing.statushistory.StatusHistoryService;
import com.ticketflow1.ticketing.ticket.dto.CreateTicketRequest;
import com.ticketflow1.ticketing.ticket.dto.TicketDetailResponse;
import com.ticketflow1.ticketing.user.AppUser;
import com.ticketflow1.ticketing.user.AppUserRepository;
import com.ticketflow1.ticketing.workflow.TicketType;
import com.ticketflow1.ticketing.workflow.TicketTypeCapability;
import com.ticketflow1.ticketing.workflow.TicketTypeRepository;
import com.ticketflow1.ticketing.workflow.TicketTransitionService;
import com.ticketflow1.ticketing.workflow.Workflow;
import com.ticketflow1.ticketing.workflow.WorkflowState;
import com.ticketflow1.ticketing.workflow.WorkflowStateRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketTypeRepository ticketTypeRepository;
    @Mock
    private WorkflowStateRepository workflowStateRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private TicketKeyGenerator ticketKeyGenerator;
    @Mock
    private AuditService auditService;
    @Mock
    private StatusHistoryService statusHistoryService;
    @Mock
    private TicketTransitionService ticketTransitionService;
    @Mock private ProposalDetailService proposalDetailService;
    @Mock private com.ticketflow1.ticketing.team.DeveloperTeamRepository developerTeamRepository;
    @Mock private com.ticketflow1.ticketing.ticketconfig.TicketSubtypeRepository subtypeRepository;
    @Mock private com.ticketflow1.ticketing.ticketconfig.SubtypeRoutingRuleRepository routingRuleRepository;
    @Mock private com.ticketflow1.ticketing.ticketconfig.SubtypeFieldDefinitionRepository fieldDefinitionRepository;
    @Mock private com.ticketflow1.ticketing.ticketconfig.SubtypeFieldOptionRepository fieldOptionRepository;
    @Mock private com.ticketflow1.ticketing.ticketconfig.TicketFieldValueRepository fieldValueRepository;
    @Mock private com.ticketflow1.ticketing.ticketconfig.DynamicFieldValidator dynamicFieldValidator;
    private com.ticketflow1.ticketing.sla.SlaCalculator slaCalculator;
    private com.ticketflow1.ticketing.sla.SlaStatusService slaStatusService;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        slaCalculator = new com.ticketflow1.ticketing.sla.SlaCalculator();
        slaStatusService = new com.ticketflow1.ticketing.sla.SlaStatusService(
                slaCalculator, java.time.Clock.systemUTC());
        ticketService = new TicketService(ticketRepository, ticketTypeRepository, workflowStateRepository,
                appUserRepository, organizationRepository, ticketKeyGenerator, auditService,
                statusHistoryService, ticketTransitionService, proposalDetailService,
                slaCalculator, slaStatusService, java.time.Clock.systemUTC(), developerTeamRepository,
                subtypeRepository, routingRuleRepository, fieldDefinitionRepository, fieldOptionRepository,
                fieldValueRepository, dynamicFieldValidator);
    }

    @ParameterizedTest
    @CsvSource({
        "CHANGE_REQUEST,SUBMITTED,false",
        "TASK,SUBMITTED,false",
        "DEFECT,REPORTED,true"
    })
    void createTicket_setsInitialStateOrgAuditAndHistory(String typeKey, String initialStateKey,
            boolean defectType) {
        Organization organization = organization(7L, "Client A");
        AppUser actor = appUser(11L, "client-a@demo.test", "Client A User", Responsibility.CLIENT, organization);
        Workflow workflow = workflow(21L, typeKey + " workflow");
        TicketType ticketType = ticketType(31L, typeKey, workflow, organization, defectType);
        WorkflowState initialState = workflowState(41L, workflow, initialStateKey, true);
        AuthPrincipal principal = new AuthPrincipal(actor.getId(), actor.getParty(), organization.getId(),
                Set.of("TICKET_CREATE", "TICKET_READ"));
        CreateTicketRequest request = new CreateTicketRequest(typeKey, "Test title", "Test description",
                Priority.HIGH, defectType ? Severity.SEV_2 : null, null, null, null, null,
                null, null, null, null);

        when(appUserRepository.findById(actor.getId())).thenReturn(java.util.Optional.of(actor));
        when(ticketTypeRepository.findByOrganizationIdAndKey(organization.getId(), typeKey))
                .thenReturn(java.util.Optional.of(ticketType));
        when(workflowStateRepository.findByWorkflowIdAndInitialTrue(workflow.getId()))
                .thenReturn(java.util.Optional.of(initialState));
        when(ticketKeyGenerator.nextKey()).thenReturn("TF-1001");
        when(ticketTransitionService.allowedTransitions(any(Ticket.class), eq(principal)))
                .thenReturn(List.of("ANALYSIS"));
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 501L);
            ReflectionTestUtils.setField(saved, "createdAt", Instant.parse("2026-07-09T10:00:00Z"));
            ReflectionTestUtils.setField(saved, "updatedAt", Instant.parse("2026-07-09T10:00:00Z"));
            return saved;
        });

        TicketDetailResponse response = ticketService.createTicket(request, principal);

        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository, org.mockito.Mockito.atLeastOnce()).saveAndFlush(ticketCaptor.capture());
        Ticket savedTicket = ticketCaptor.getValue();
        assertThat(savedTicket.getTicketKey()).isEqualTo("TF-1001");
        assertThat(savedTicket.getTicketType().getKey()).isEqualTo(typeKey);
        assertThat(savedTicket.getCurrentState().getKey()).isEqualTo(initialStateKey);
        assertThat(savedTicket.getOrganization().getId()).isEqualTo(organization.getId());
        assertThat(savedTicket.getSeverity()).isEqualTo(defectType ? Severity.SEV_2 : null);
        assertThat(response.status()).isEqualTo(initialStateKey);
        assertThat(response.type()).isEqualTo(typeKey);
        assertThat(response.allowedTransitions()).containsExactly("ANALYSIS");

        verify(auditService).record(savedTicket, actor.getId(), AuditAction.TICKET_CREATED);
        verify(statusHistoryService).record(savedTicket, null, initialState, actor.getId());
    }

    @Test
    void createTicket_rejectsSeverityForNonDefect() {
        Organization organization = organization(7L, "Client A");
        AppUser actor = appUser(11L, "client-a@demo.test", "Client A User", Responsibility.CLIENT, organization);
        Workflow workflow = workflow(21L, "Task workflow");
        TicketType ticketType = ticketType(31L, "TASK", workflow, organization, false);
        WorkflowState initialState = workflowState(41L, workflow, "SUBMITTED", true);
        AuthPrincipal principal = new AuthPrincipal(actor.getId(), actor.getParty(), organization.getId(),
                Set.of("TICKET_CREATE"));
        CreateTicketRequest request = new CreateTicketRequest("TASK", "Task", "Desc", Priority.MEDIUM,
                Severity.SEV_1, null, null, null, null, null, null, null, null);

        when(appUserRepository.findById(actor.getId())).thenReturn(java.util.Optional.of(actor));
        when(ticketTypeRepository.findByOrganizationIdAndKey(organization.getId(), "TASK"))
                .thenReturn(java.util.Optional.of(ticketType));
        when(workflowStateRepository.findByWorkflowIdAndInitialTrue(workflow.getId()))
                .thenReturn(java.util.Optional.of(initialState));

        assertThatThrownBy(() -> ticketService.createTicket(request, principal))
                .hasMessageContaining("severity is allowed only when type is DEFECT");
    }

    @Test
    void updateTicket_recomputesAndAuditsSeverityFromOriginalCreationTime() {
        Organization organization = organization(7L, "Client A");
        AppUser actor = appUser(11L, "admin@ticketflow1.demo", "Admin", Responsibility.TICKETFLOW1, null);
        Workflow workflow = workflow(21L, "Defect workflow");
        TicketType type = ticketType(31L, "DEFECT", workflow, organization, true);
        WorkflowState analysis = workflowState(41L, workflow, "ANALYSIS", false);
        Ticket ticket = new Ticket("TF-1002", type, analysis, Priority.HIGH, Severity.SEV_1,
                "Defect", "Description", organization, actor, Responsibility.TICKETFLOW1);
        Instant createdAt = Instant.parse("2026-07-13T08:00:00Z");
        ReflectionTestUtils.setField(ticket, "id", 501L);
        ReflectionTestUtils.setField(ticket, "createdAt", createdAt);
        ReflectionTestUtils.setField(ticket, "updatedAt", createdAt);
        AuthPrincipal principal = new AuthPrincipal(actor.getId(), Responsibility.TICKETFLOW1, null,
                Set.of("TICKET_UPDATE", "TICKET_READ"));

        when(ticketRepository.findByTicketKey("TF-1002")).thenReturn(java.util.Optional.of(ticket));
        when(appUserRepository.findById(actor.getId())).thenReturn(java.util.Optional.of(actor));
        when(ticketRepository.save(ticket)).thenReturn(ticket);
        when(ticketTransitionService.allowedTransitions(ticket, principal)).thenReturn(List.of());

        ticketService.updateTicket("TF-1002",
                new com.ticketflow1.ticketing.ticket.dto.UpdateTicketRequest(
                        null, null, null, null, Severity.SEV_3, null, null, null, null), principal);

        assertThat(ticket.getSeverity()).isEqualTo(Severity.SEV_3);
        assertThat(ticket.getResponseDueAt()).isEqualTo(createdAt.plusSeconds(60 * 60));
        assertThat(ticket.getFirstInfoDueAt()).isEqualTo(createdAt.plusSeconds(90 * 60));
        assertThat(ticket.getNextUpdateDueAt()).isNull();
        verify(auditService).record(ticket, actor.getId(), AuditAction.SEVERITY_CHANGED,
                "severity", "SEV_1", "SEV_3");
    }

    private static Organization organization(Long id, String name) {
        Organization organization = new Organization(name);
        ReflectionTestUtils.setField(organization, "id", id);
        return organization;
    }

    private static Workflow workflow(Long id, String name) {
        Workflow workflow = new Workflow(name, null);
        ReflectionTestUtils.setField(workflow, "id", id);
        return workflow;
    }

    private static TicketType ticketType(Long id, String key, Workflow workflow,
            Organization organization, boolean defectType) {
        TicketType ticketType = new TicketType(key, key, workflow, organization, false, false);
        ticketType.configure(key, workflow, true, 0,
                defectType ? TicketTypeCapability.DEFECT_SLA : TicketTypeCapability.STANDARD);
        ReflectionTestUtils.setField(ticketType, "id", id);
        return ticketType;
    }

    private static WorkflowState workflowState(Long id, Workflow workflow, String key, boolean initial) {
        WorkflowState workflowState = new WorkflowState(workflow, key, initial, false, 10);
        ReflectionTestUtils.setField(workflowState, "id", id);
        return workflowState;
    }

    private static AppUser appUser(Long id, String email, String displayName,
            Responsibility party, Organization organization) {
        Role role = new Role("Client User", party, organization, false);
        ReflectionTestUtils.setField(role, "id", 99L);
        AppUser user = new AppUser(email, "hash", displayName, party, role, organization);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
