package com.ticketflow1.ticketing.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ticketflow1.ticketing.audit.AuditAction;
import com.ticketflow1.ticketing.audit.AuditService;
import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.comment.CommentService;
import com.ticketflow1.ticketing.comment.CommentVisibility;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.common.IllegalTransitionException;
import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.proposal.ProposalDetailService;
import com.ticketflow1.ticketing.rbac.Permission;
import com.ticketflow1.ticketing.rbac.Role;
import com.ticketflow1.ticketing.statushistory.StatusHistoryService;
import com.ticketflow1.ticketing.team.DeveloperTeam;
import com.ticketflow1.ticketing.ticket.Priority;
import com.ticketflow1.ticketing.ticket.Responsibility;
import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.ticket.TicketRepository;
import com.ticketflow1.ticketing.ticket.dto.TicketDetailResponse;
import com.ticketflow1.ticketing.ticketconfig.TicketApproval;
import com.ticketflow1.ticketing.ticketconfig.TicketApprovalRepository;
import com.ticketflow1.ticketing.ticketconfig.TicketApprovalStatus;
import com.ticketflow1.ticketing.ticketconfig.TicketDecisionRepository;
import com.ticketflow1.ticketing.user.AppUser;
import com.ticketflow1.ticketing.user.AppUserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TicketTransitionServiceTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private WorkflowStateRepository workflowStateRepository;
    @Mock
    private WorkflowTransitionRepository workflowTransitionRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private StatusHistoryService statusHistoryService;
    @Mock
    private CommentService commentService;
    @Mock private ProposalDetailService proposalDetailService;
    @Mock private TicketApprovalRepository ticketApprovalRepository;
    @Mock private TicketDecisionRepository ticketDecisionRepository;
    private com.ticketflow1.ticketing.sla.SlaStatusService slaStatusService;

    private TicketTransitionService ticketTransitionService;

    @BeforeEach
    void setUp() {
        var calculator = new com.ticketflow1.ticketing.sla.SlaCalculator();
        slaStatusService = new com.ticketflow1.ticketing.sla.SlaStatusService(
                calculator, java.time.Clock.systemUTC());
        ticketTransitionService = new TicketTransitionService(ticketRepository, workflowStateRepository,
                workflowTransitionRepository, appUserRepository, auditService, statusHistoryService, commentService,
                proposalDetailService, slaStatusService, ticketApprovalRepository, ticketDecisionRepository,
                java.time.Clock.systemUTC());
    }

    @ParameterizedTest
    @MethodSource("legalTransitions")
    void legalTransition_succeedsForActorWithRequiredPermissionAndParty(String workflowName,
            String fromKey, String toKey, String permissionKey, Responsibility actorParty,
            Responsibility responsibilityAfter) {
        Fixture fixture = fixture(workflowName, fromKey, toKey, permissionKey, actorParty, responsibilityAfter);
        AuthPrincipal principal = new AuthPrincipal(fixture.actor().getId(), actorParty,
                fixture.organization().getId(), Set.of(permissionKey));

        stubSuccessPath(fixture, principal, toKey);

        TicketDetailResponse response = ticketTransitionService.transition(
                fixture.ticket().getTicketKey(), toKey, null, principal);

        assertThat(response.status()).isEqualTo(toKey);
        assertThat(fixture.ticket().getCurrentState().getKey()).isEqualTo(toKey);
        assertThat(fixture.ticket().getCurrentResponsibility())
                .isEqualTo(responsibilityAfter == null ? Responsibility.TICKETFLOW1 : responsibilityAfter);
        verify(statusHistoryService).record(fixture.ticket(), fixture.fromState(), fixture.toState(),
                fixture.actor().getId());
        verify(auditService).record(fixture.ticket(), fixture.actor().getId(), AuditAction.STATUS_CHANGED,
                "status", fromKey, toKey);
    }

    @ParameterizedTest
    @MethodSource("legalTransitions")
    void legalTransition_failsWhenActorLacksPermission(String workflowName,
            String fromKey, String toKey, String permissionKey, Responsibility actorParty,
            Responsibility responsibilityAfter) {
        Fixture fixture = fixture(workflowName, fromKey, toKey, permissionKey, actorParty, responsibilityAfter);
        AuthPrincipal principal = new AuthPrincipal(fixture.actor().getId(), actorParty,
                fixture.organization().getId(), Set.of("SOME_OTHER_PERMISSION"));

        stubLookupPath(fixture, principal, toKey);

        assertThatThrownBy(() -> ticketTransitionService.transition(
                fixture.ticket().getTicketKey(), toKey, null, principal))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("Cannot move ticket");
    }

    @ParameterizedTest
    @MethodSource("partyRestrictedTransitions")
    void legalTransition_failsWhenActorHasWrongParty(String workflowName,
            String fromKey, String toKey, String permissionKey, Responsibility requiredParty,
            Responsibility wrongParty, Responsibility responsibilityAfter) {
        Fixture fixture = fixture(workflowName, fromKey, toKey, permissionKey, requiredParty, responsibilityAfter);
        AuthPrincipal principal = new AuthPrincipal(fixture.actor().getId(), wrongParty,
                fixture.organization().getId(), Set.of(permissionKey));

        stubLookupPath(fixture, principal, toKey);

        assertThatThrownBy(() -> ticketTransitionService.transition(
                fixture.ticket().getTicketKey(), toKey, null, principal))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("Cannot move ticket");
    }

    @Test
    void undefinedTransition_isRejectedRegardlessOfPermission() {
        Fixture fixture = fixture("Change Request Workflow", "SUBMITTED", "ANALYSIS",
                "TICKET_TRANSITION", Responsibility.TICKETFLOW1, null);
        AuthPrincipal principal = new AuthPrincipal(fixture.actor().getId(), Responsibility.TICKETFLOW1,
                fixture.organization().getId(), Set.of("TICKET_TRANSITION"));

        when(ticketRepository.findByTicketKey(fixture.ticket().getTicketKey()))
                .thenReturn(java.util.Optional.of(fixture.ticket()));
        when(workflowStateRepository.findByWorkflowIdAndKey(fixture.workflow().getId(), "DEVELOPMENT"))
                .thenReturn(java.util.Optional.of(workflowState(77L, fixture.workflow(), "DEVELOPMENT", false)));
        when(workflowTransitionRepository.findByWorkflowIdAndFromStateIdAndToStateId(
                fixture.workflow().getId(), fixture.fromState().getId(), 77L))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> ticketTransitionService.transition(
                fixture.ticket().getTicketKey(), "DEVELOPMENT", null, principal))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("SUBMITTED")
                .hasMessageContaining("DEVELOPMENT");
    }

    @Test
    void transitionComment_isPersistedAsPublicInTheTransitionTransaction() {
        Fixture fixture = fixture("Change Request Workflow", "SUBMITTED", "ANALYSIS",
                "TICKET_TRANSITION", Responsibility.TICKETFLOW1, null);
        AuthPrincipal principal = new AuthPrincipal(fixture.actor().getId(), Responsibility.TICKETFLOW1,
                fixture.organization().getId(), Set.of("TICKET_TRANSITION"));
        stubSuccessPath(fixture, principal, "ANALYSIS");

        ticketTransitionService.transition(fixture.ticket().getTicketKey(), "ANALYSIS",
                "Please investigate", principal);

        verify(commentService).createForTicket(fixture.ticket(), "Please investigate",
                CommentVisibility.PUBLIC, principal);
    }

    @Test
    void workflowApproval_requiresResolvedApprover() {
        Fixture fixture = fixture("TASI Workflow", "PENDING_APPROVAL", "IMPLEMENTATION",
                "TICKET_TRANSITION", Responsibility.TICKETFLOW1, null);
        WorkflowTransition protectedEdge = new WorkflowTransition(fixture.workflow(), fixture.fromState(),
                fixture.toState(), new Permission("TICKET_TRANSITION"), Responsibility.TICKETFLOW1, null,
                TransitionOperationKind.WORKFLOW_APPROVE);
        when(workflowTransitionRepository.findByWorkflowIdAndFromStateId(fixture.workflow().getId(), fixture.fromState().getId()))
                .thenReturn(List.of(protectedEdge));
        AuthPrincipal principal = new AuthPrincipal(fixture.actor().getId(), Responsibility.TICKETFLOW1,
                fixture.organization().getId(), Set.of("TICKET_TRANSITION"));

        assertThatThrownBy(() -> ticketTransitionService.transitionOwned(fixture.ticket(),
                TransitionOperationKind.WORKFLOW_APPROVE, principal))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getErrorCode())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void protectedApproval_isNotExposedByGenericTransitions() {
        Fixture fixture = fixture("TASI Workflow", "PENDING_APPROVAL", "IMPLEMENTATION",
                "TICKET_TRANSITION", Responsibility.TICKETFLOW1, null);
        WorkflowTransition protectedEdge = new WorkflowTransition(fixture.workflow(), fixture.fromState(),
                fixture.toState(), new Permission("TICKET_TRANSITION"), Responsibility.TICKETFLOW1, null,
                TransitionOperationKind.WORKFLOW_APPROVE);
        when(workflowTransitionRepository.findByWorkflowIdAndFromStateId(fixture.workflow().getId(), fixture.fromState().getId()))
                .thenReturn(List.of(protectedEdge));
        AuthPrincipal principal = new AuthPrincipal(fixture.actor().getId(), Responsibility.TICKETFLOW1,
                fixture.organization().getId(), Set.of("TICKET_TRANSITION"));

        assertThat(ticketTransitionService.allowedTransitions(fixture.ticket(), principal)).isEmpty();
    }

    @Test
    void workflowApproval_succeedsOnlyForResolvedApprover() {
        Fixture fixture = fixture("TASI Workflow", "PENDING_APPROVAL", "IMPLEMENTATION",
                "TICKET_TRANSITION", Responsibility.TICKETFLOW1, null);
        ReflectionTestUtils.setField(fixture.ticket(), "resolvedApprover", fixture.actor());
        TicketApproval approval = new TicketApproval(
                fixture.ticket(), fixture.fromState(), fixture.actor(), null);
        WorkflowTransition edge = new WorkflowTransition(fixture.workflow(), fixture.fromState(), fixture.toState(),
                new Permission("TICKET_TRANSITION"), Responsibility.TICKETFLOW1, null,
                TransitionOperationKind.WORKFLOW_APPROVE);
        when(workflowTransitionRepository.findByWorkflowIdAndFromStateId(fixture.workflow().getId(), fixture.fromState().getId()))
                .thenReturn(List.of(edge));
        when(ticketApprovalRepository.findByTicketIdAndStatus(
                fixture.ticket().getId(), TicketApprovalStatus.PENDING))
                .thenReturn(java.util.Optional.of(approval));
        when(appUserRepository.findById(fixture.actor().getId())).thenReturn(java.util.Optional.of(fixture.actor()));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuthPrincipal principal = new AuthPrincipal(fixture.actor().getId(), Responsibility.TICKETFLOW1,
                fixture.organization().getId(), Set.of("TICKET_TRANSITION"));

        ticketTransitionService.transitionOwned(fixture.ticket(), TransitionOperationKind.WORKFLOW_APPROVE, principal);

        assertThat(fixture.ticket().getCurrentState().getKey()).isEqualTo("IMPLEMENTATION");
    }

    /**
     * Feature 003 regression baseline.
     *
     * This test intentionally fails until Phase 1 implements the documented
     * team-lead fallback. A TASI routed to a team without an explicit approver
     * currently exposes no decision command and rejects its team leader, which
     * is the production symptom reported as "nobody can approve".
     */
    @Test
    void workflowApproval_allowsAssignedTeamLeaderWhenNoExplicitApprover() {
        Fixture fixture = fixture("TASI Workflow", "PENDING_APPROVAL", "IMPLEMENTATION",
                "TICKET_TRANSITION", Responsibility.TICKETFLOW1, null);
        DeveloperTeam assignedTeam = new DeveloperTeam(
                "TASI Delivery", "Approves TASI implementation", fixture.actor(), fixture.actor());
        ReflectionTestUtils.setField(assignedTeam, "id", 67L);
        fixture.ticket().replaceTeams(Set.of(assignedTeam));
        TicketApproval approval = new TicketApproval(
                fixture.ticket(), fixture.fromState(), null, assignedTeam);
        WorkflowTransition edge = new WorkflowTransition(fixture.workflow(), fixture.fromState(), fixture.toState(),
                new Permission("TICKET_TRANSITION"), Responsibility.TICKETFLOW1, null,
                TransitionOperationKind.WORKFLOW_APPROVE);
        when(workflowTransitionRepository.findByWorkflowIdAndFromStateId(
                fixture.workflow().getId(), fixture.fromState().getId())).thenReturn(List.of(edge));
        when(ticketApprovalRepository.findByTicketIdAndStatus(
                fixture.ticket().getId(), TicketApprovalStatus.PENDING))
                .thenReturn(java.util.Optional.of(approval));
        when(appUserRepository.findById(fixture.actor().getId()))
                .thenReturn(java.util.Optional.of(fixture.actor()));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuthPrincipal principal = new AuthPrincipal(fixture.actor().getId(), Responsibility.TICKETFLOW1,
                fixture.organization().getId(), Set.of("TICKET_TRANSITION"));

        ticketTransitionService.transitionOwned(
                fixture.ticket(), TransitionOperationKind.WORKFLOW_APPROVE, principal);

        assertThat(fixture.ticket().getCurrentState().getKey()).isEqualTo("IMPLEMENTATION");
    }

    @Test
    void protectedApproval_persistsDecisionClosesApprovalAndAuditsAtomically() {
        Fixture fixture = fixture("TASI Workflow", "PENDING_APPROVAL", "IMPLEMENTATION",
                "TICKET_TRANSITION", Responsibility.TICKETFLOW1, null);
        TicketApproval approval = new TicketApproval(
                fixture.ticket(), fixture.fromState(), fixture.actor(), null);
        WorkflowTransition edge = new WorkflowTransition(fixture.workflow(), fixture.fromState(), fixture.toState(),
                new Permission("TICKET_TRANSITION"), Responsibility.TICKETFLOW1, null,
                TransitionOperationKind.WORKFLOW_APPROVE);
        AuthPrincipal principal = new AuthPrincipal(fixture.actor().getId(), Responsibility.TICKETFLOW1,
                fixture.organization().getId(), Set.of("TICKET_TRANSITION"));
        when(ticketRepository.findByTicketKey(fixture.ticket().getTicketKey()))
                .thenReturn(java.util.Optional.of(fixture.ticket()));
        when(ticketApprovalRepository.findForUpdate(
                fixture.ticket().getId(), TicketApprovalStatus.PENDING))
                .thenReturn(java.util.Optional.of(approval));
        when(ticketApprovalRepository.findByTicketIdAndStatus(
                fixture.ticket().getId(), TicketApprovalStatus.PENDING))
                .thenReturn(java.util.Optional.of(approval));
        when(workflowTransitionRepository.findByWorkflowIdAndFromStateId(
                fixture.workflow().getId(), fixture.fromState().getId())).thenReturn(List.of(edge));
        when(workflowTransitionRepository.findByWorkflowIdAndFromStateId(
                fixture.workflow().getId(), fixture.toState().getId())).thenReturn(List.of());
        when(appUserRepository.findById(fixture.actor().getId()))
                .thenReturn(java.util.Optional.of(fixture.actor()));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketDetailResponse response = ticketTransitionService.protectedDecision(
                fixture.ticket().getTicketKey(), TransitionOperationKind.WORKFLOW_APPROVE,
                "Ready to implement", principal);

        assertThat(response.status()).isEqualTo("IMPLEMENTATION");
        assertThat(approval.getStatus()).isEqualTo(TicketApprovalStatus.APPROVED);
        assertThat(approval.getDecidedBy()).isEqualTo(fixture.actor());
        verify(ticketApprovalRepository).save(approval);
        verify(ticketDecisionRepository).save(any(com.ticketflow1.ticketing.ticketconfig.TicketDecision.class));
        verify(auditService).record(fixture.ticket(), fixture.actor().getId(),
                AuditAction.WORKFLOW_APPROVED, "approvalDecision", "PENDING", "APPROVED");
        verify(statusHistoryService).record(
                fixture.ticket(), fixture.fromState(), fixture.toState(), fixture.actor().getId());
    }

    @Test
    void clientAcceptance_requiresTheBusinessOwner() {
        Fixture fixture = fixture("REQ Workflow", "CLIENT_ACCEPTANCE", "DEPLOYMENT",
                "TICKET_TRANSITION", Responsibility.CLIENT, Responsibility.TICKETFLOW1);
        ReflectionTestUtils.setField(fixture.ticket(), "businessOwner", fixture.actor());
        WorkflowTransition edge = new WorkflowTransition(fixture.workflow(), fixture.fromState(), fixture.toState(),
                new Permission("TICKET_TRANSITION"), Responsibility.CLIENT, Responsibility.TICKETFLOW1,
                TransitionOperationKind.CLIENT_ACCEPT);
        when(workflowTransitionRepository.findByWorkflowIdAndFromStateId(fixture.workflow().getId(), fixture.fromState().getId()))
                .thenReturn(List.of(edge));
        when(appUserRepository.findById(fixture.actor().getId())).thenReturn(java.util.Optional.of(fixture.actor()));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuthPrincipal principal = new AuthPrincipal(fixture.actor().getId(), Responsibility.CLIENT,
                fixture.organization().getId(), Set.of("TICKET_TRANSITION"));

        ticketTransitionService.transitionOwned(fixture.ticket(), TransitionOperationKind.CLIENT_ACCEPT, principal);

        assertThat(fixture.ticket().getCurrentState().getKey()).isEqualTo("DEPLOYMENT");
        assertThat(fixture.ticket().getCurrentResponsibility()).isEqualTo(Responsibility.TICKETFLOW1);
    }

    private void stubSuccessPath(Fixture fixture, AuthPrincipal principal, String toKey) {
        stubLookupPath(fixture, principal, toKey);
        when(appUserRepository.findById(fixture.actor().getId())).thenReturn(java.util.Optional.of(fixture.actor()));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowTransitionRepository.findByWorkflowIdAndFromStateId(fixture.workflow().getId(),
                fixture.toState().getId())).thenReturn(List.of());
    }

    private void stubLookupPath(Fixture fixture, AuthPrincipal principal, String toKey) {
        if (principal.party() == Responsibility.CLIENT) {
            when(ticketRepository.findByTicketKeyAndOrganizationId(
                    fixture.ticket().getTicketKey(), fixture.organization().getId()))
                    .thenReturn(java.util.Optional.of(fixture.ticket()));
        } else {
            when(ticketRepository.findByTicketKey(fixture.ticket().getTicketKey()))
                    .thenReturn(java.util.Optional.of(fixture.ticket()));
        }
        when(workflowStateRepository.findByWorkflowIdAndKey(fixture.workflow().getId(), toKey))
                .thenReturn(java.util.Optional.of(fixture.toState()));
        when(workflowTransitionRepository.findByWorkflowIdAndFromStateIdAndToStateId(
                fixture.workflow().getId(), fixture.fromState().getId(), fixture.toState().getId()))
                .thenReturn(java.util.Optional.of(fixture.transition()));
    }

    private static Stream<Arguments> legalTransitions() {
        return Stream.of(
                Arguments.of("Change Request Workflow", "SUBMITTED", "ANALYSIS",
                        "TICKET_TRANSITION", Responsibility.TICKETFLOW1, null),
                Arguments.of("Change Request Workflow", "SUBMITTED", "CANCELLED",
                        "TICKET_CANCEL", Responsibility.TICKETFLOW1, null),
                Arguments.of("Change Request Workflow", "ANALYSIS", "PROPOSAL",
                        "TICKET_TRANSITION", Responsibility.TICKETFLOW1, Responsibility.CLIENT),
                Arguments.of("Change Request Workflow", "PROPOSAL", "PROPOSAL_APPROVED",
                        "PROPOSAL_APPROVE", Responsibility.CLIENT, Responsibility.TICKETFLOW1),
                Arguments.of("Task Workflow", "SUBMITTED", "ANALYSIS",
                        "TICKET_TRANSITION", Responsibility.TICKETFLOW1, null),
                Arguments.of("Task Workflow", "ANALYSIS", "DEVELOPMENT",
                        "TICKET_TRANSITION", Responsibility.TICKETFLOW1, null),
                Arguments.of("Defect Workflow", "REPORTED", "ANALYSIS",
                        "TICKET_TRANSITION", Responsibility.TICKETFLOW1, null),
                Arguments.of("Defect Workflow", "FIX_IN_PROGRESS", "CLIENT_CONFIRMATION",
                        "TICKET_TRANSITION", Responsibility.TICKETFLOW1, Responsibility.CLIENT),
                Arguments.of("Defect Workflow", "CLIENT_CONFIRMATION", "CLOSED",
                        "TICKET_TRANSITION", Responsibility.CLIENT, null));
    }

    private static Stream<Arguments> partyRestrictedTransitions() {
        return Stream.of(
                Arguments.of("Change Request Workflow", "PROPOSAL", "PROPOSAL_APPROVED",
                        "PROPOSAL_APPROVE", Responsibility.CLIENT, Responsibility.TICKETFLOW1,
                        Responsibility.TICKETFLOW1),
                Arguments.of("Defect Workflow", "CLIENT_CONFIRMATION", "CLOSED",
                        "TICKET_TRANSITION", Responsibility.CLIENT, Responsibility.TICKETFLOW1, null),
                Arguments.of("Task Workflow", "SUBMITTED", "ANALYSIS",
                        "TICKET_TRANSITION", Responsibility.TICKETFLOW1, Responsibility.CLIENT, null));
    }

    private static Fixture fixture(String workflowName, String fromKey, String toKey,
            String permissionKey, Responsibility requiredParty, Responsibility responsibilityAfter) {
        Organization organization = organization(7L, "Client A");
        Workflow workflow = workflow(17L, workflowName);
        WorkflowState fromState = workflowState(27L, workflow, fromKey, false);
        WorkflowState toState = workflowState(28L, workflow, toKey, false);
        Permission permission = new Permission(permissionKey);
        TicketType ticketType = ticketType(37L, workflowName, workflow, organization);
        Ticket ticket = ticket(47L, "TF-1001", ticketType, fromState, organization);
        AppUser actor = appUser(57L, requiredParty == Responsibility.CLIENT ? "client@demo.test"
                : "agent@demo.test", requiredParty, organization);
        WorkflowTransition transition = new WorkflowTransition(workflow, fromState, toState,
                permission, requiredParty, responsibilityAfter);
        return new Fixture(organization, workflow, fromState, toState, transition, ticket, actor);
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

    private static WorkflowState workflowState(Long id, Workflow workflow, String key, boolean initial) {
        WorkflowState workflowState = new WorkflowState(workflow, key, initial, false, 10);
        ReflectionTestUtils.setField(workflowState, "id", id);
        return workflowState;
    }

    private static TicketType ticketType(Long id, String key, Workflow workflow, Organization organization) {
        TicketType ticketType = new TicketType(key, key, workflow, organization, false, false);
        ReflectionTestUtils.setField(ticketType, "id", id);
        return ticketType;
    }

    private static Ticket ticket(Long id, String ticketKey, TicketType ticketType,
            WorkflowState currentState, Organization organization) {
        Role role = new Role("Role", Responsibility.CLIENT, organization, false);
        ReflectionTestUtils.setField(role, "id", 99L);
        AppUser owner = new AppUser("owner@demo.test", "hash", "Owner", Responsibility.CLIENT, role, organization);
        ReflectionTestUtils.setField(owner, "id", 88L);
        Ticket ticket = new Ticket(ticketKey, ticketType, currentState, Priority.MEDIUM, null,
                "Title", "Description", organization, owner, Responsibility.TICKETFLOW1);
        ReflectionTestUtils.setField(ticket, "id", id);
        ReflectionTestUtils.setField(ticket, "createdAt", Instant.parse("2026-07-10T10:00:00Z"));
        ReflectionTestUtils.setField(ticket, "updatedAt", Instant.parse("2026-07-10T10:00:00Z"));
        return ticket;
    }

    private static AppUser appUser(Long id, String email, Responsibility party, Organization organization) {
        Role role = new Role("Role", party, organization, false);
        ReflectionTestUtils.setField(role, "id", 100L);
        AppUser user = new AppUser(email, "hash", "Actor", party, role, organization);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private record Fixture(
            Organization organization,
            Workflow workflow,
            WorkflowState fromState,
            WorkflowState toState,
            WorkflowTransition transition,
            Ticket ticket,
            AppUser actor) {
    }
}
