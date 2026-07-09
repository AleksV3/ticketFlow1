package com.ticketflow1.ticketing.ticket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.user.AppUser;
import java.time.Instant;
import java.util.List;

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
        List<String> allowedTransitions) {

    public static TicketDetailResponse from(Ticket ticket, List<String> allowedTransitions) {
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
                null,
                allowedTransitions);
    }

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
            String status) {
    }
}
