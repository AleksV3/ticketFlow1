package com.ticketflow1.ticketing.ticket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.user.AppUser;
import java.time.Instant;
import java.util.List;
import com.ticketflow1.ticketing.proposal.ProposalDetailService.ProposalDetail;
import com.ticketflow1.ticketing.proposal.dto.ChangeProposalResponse;
import com.ticketflow1.ticketing.sla.SlaStatus;
import com.ticketflow1.ticketing.workflow.WorkflowState;
import com.ticketflow1.ticketing.workflow.WorkflowTransition;

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
        String assignedTeam,
        String currentResponsibility,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt,
        SlaRef sla,
        ProcessMap processMap,
        List<String> allowedTransitions,
        ChangeProposalResponse latestProposal,
        List<String> proposalCommands) {

    public static TicketDetailResponse from(Ticket ticket, List<String> allowedTransitions, ProposalDetail proposal,
            SlaStatus slaStatus) {
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
                ticket.getAssignedTeam(),
                ticket.getCurrentResponsibility().name(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getClosedAt(),
                "DEFECT".equals(ticket.getTicketType().getKey()) ? SlaRef.from(ticket, slaStatus) : null,
                ProcessMap.from(ticket),
                allowedTransitions,
                proposal == null ? null : proposal.latestProposal(),
                proposal == null ? List.of() : proposal.permittedCommands());
    }

    public record ProcessMap(String name, List<ProcessState> states, List<ProcessTransition> transitions) {
        static ProcessMap from(Ticket ticket) {
            var workflow = ticket.getTicketType().getWorkflow();
            return new ProcessMap(workflow.getName(),
                    workflow.getStates().stream().sorted(java.util.Comparator.comparingInt(WorkflowState::getSortOrder))
                            .map(state -> new ProcessState(state.getId(), state.getKey(), state.isInitial(), state.isTerminal())).toList(),
                    workflow.getTransitions().stream().sorted(java.util.Comparator.comparing(WorkflowTransition::getId))
                            .map(edge -> new ProcessTransition(edge.getFromState().getId(), edge.getToState().getId())).toList());
        }
    }

    public record ProcessState(Long id, String key, boolean isInitial, boolean isTerminal) {}
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
