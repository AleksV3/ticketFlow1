package com.ticketflow1.ticketing.ticket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.user.AppUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.ticketflow1.ticketing.proposal.ProposalDetailService.ProposalDetail;
import com.ticketflow1.ticketing.proposal.dto.ChangeProposalResponse;
import com.ticketflow1.ticketing.sla.SlaStatus;
import com.ticketflow1.ticketing.workflow.WorkflowState;
import com.ticketflow1.ticketing.workflow.WorkflowTransition;
import com.ticketflow1.ticketing.team.DeveloperTeam;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TicketDetailResponse(
        Long id,
        String ticketKey,
        String type,
        String status,
        String priority,
        String severity,
        String title,
        String description,
        OrganizationRef organization,
        UserRef businessOwner,
        UserRef ticketLead,
        List<UserRef> developers,
        List<TeamRef> teams,
        String assignedTeam,
        String currentResponsibility,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt,
        SlaRef sla,
        ProcessMap processMap,
        List<String> allowedTransitions,
        ChangeProposalResponse latestProposal,
        List<String> proposalCommands,
        String subtype,
        Long subtypeId,
        String parentTicketKey,
        List<ChildTicketRef> childTickets,
        UserRef targetUser,
        String targetUserDisplaySnapshot,
        Long routingRuleId,
        Long resolvedApproverId,
        Map<String,Object> dynamicValues) {

    public static TicketDetailResponse from(Ticket ticket, List<String> allowedTransitions, ProposalDetail proposal,
            SlaStatus slaStatus, Map<String,Object> dynamicValues, List<Ticket> childTickets) {
        return new TicketDetailResponse(
                ticket.getId(),
                ticket.getTicketKey(),
                ticket.getTicketType().getKey(),
                ticket.getCurrentState().getKey(),
                ticket.getPriority().name(),
                ticket.getSeverity() == null ? null : ticket.getSeverity().name(),
                ticket.getTitle(),
                ticket.getDescription(),
                OrganizationRef.from(ticket.getOrganization()),
                UserRef.from(ticket.getBusinessOwner()),
                UserRef.from(ticket.getTicketLead()),
                ticket.getDevelopers().stream().map(UserRef::from)
                        .sorted(java.util.Comparator.comparing(UserRef::displayName)).toList(),
                ticket.getTeams().stream().map(TeamRef::from)
                        .sorted(java.util.Comparator.comparing(TeamRef::name)).toList(),
                ticket.getAssignedTeam(),
                ticket.getCurrentResponsibility().name(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getClosedAt(),
                ticket.getTicketType().getCapability() == com.ticketflow1.ticketing.workflow.TicketTypeCapability.DEFECT_SLA ? SlaRef.from(ticket, slaStatus) : null,
                ProcessMap.from(ticket),
                allowedTransitions,
                proposal == null ? null : proposal.latestProposal(),
                proposal == null ? List.of() : proposal.permittedCommands(),
                ticket.getSubtype() == null ? null : ticket.getSubtype().getKey(),
                ticket.getSubtype() == null ? null : ticket.getSubtype().getId(),
                ticket.getParentTicket() == null ? null : ticket.getParentTicket().getTicketKey(),
                childTickets.stream().map(ChildTicketRef::from).toList(),
                UserRef.from(ticket.getTargetUser()), ticket.getTargetUserDisplaySnapshot(),
                ticket.getRoutingRule() == null ? null : ticket.getRoutingRule().getId(),
                ticket.getResolvedApprover() == null ? null : ticket.getResolvedApprover().getId(), dynamicValues);
    }
    public static TicketDetailResponse from(Ticket ticket, List<String> allowedTransitions, ProposalDetail proposal,
            SlaStatus slaStatus, Map<String,Object> dynamicValues) {
        return from(ticket, allowedTransitions, proposal, slaStatus, dynamicValues, List.of());
    }
    public static TicketDetailResponse from(Ticket ticket, List<String> allowedTransitions, ProposalDetail proposal,
            SlaStatus slaStatus) {
        return from(ticket, allowedTransitions, proposal, slaStatus, Map.of());
    }

    public record ProcessMap(String name, List<ProcessState> states, List<ProcessTransition> transitions) {
        static ProcessMap from(Ticket ticket) {
            var workflow = ticket.getTicketType().getWorkflow();
            return new ProcessMap(workflow.getName(),
                    workflow.getStates().stream().sorted(java.util.Comparator.comparingInt(WorkflowState::getSortOrder))
                            .map(state -> new ProcessState(state.getId(), state.getKey(), state.isInitial(), state.isTerminal(), state.getSortOrder())).toList(),
                    workflow.getTransitions().stream().sorted(java.util.Comparator.comparing(WorkflowTransition::getId))
                            .map(edge -> new ProcessTransition(edge.getFromState().getId(), edge.getToState().getId())).toList());
        }
    }

    public record ProcessState(Long id, String key, boolean isInitial, boolean isTerminal, int sortOrder) {}
    public record ProcessTransition(Long fromStateId, Long toStateId) {}

    public record OrganizationRef(Long id, String name) {
        public static OrganizationRef from(Organization organization) {
            return new OrganizationRef(organization.getId(), organization.getName());
        }
    }

    public record UserRef(Long id, String displayName) {
        public static UserRef from(AppUser user) {
            if (user == null) {
                return null;
            }
            return new UserRef(user.getId(), user.getDisplayName());
        }
    }
    public record TeamRef(Long id, String name) {
        public static TeamRef from(DeveloperTeam team) { return new TeamRef(team.getId(), team.getName()); }
    }
    public record ChildTicketRef(String ticketKey, String title, String type, String status, String currentResponsibility) {
        public static ChildTicketRef from(Ticket ticket) {
            return new ChildTicketRef(ticket.getTicketKey(), ticket.getTitle(), ticket.getTicketType().getKey(),
                    ticket.getCurrentState().getKey(), ticket.getCurrentResponsibility().name());
        }
    }

    public record SlaRef(
            Instant responseDueAt,
            Instant firstInfoDueAt,
            Instant nextUpdateDueAt,
            Instant respondedAt,
            Instant firstInfoAt,
            String status) {
        public static SlaRef from(Ticket ticket, SlaStatus status) {
            return new SlaRef(ticket.getResponseDueAt(), ticket.getFirstInfoDueAt(),
                    ticket.getNextUpdateDueAt(), ticket.getRespondedAt(), ticket.getFirstInfoAt(), status.name());
        }
    }
}
