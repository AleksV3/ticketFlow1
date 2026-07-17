package com.ticketflow1.ticketing.ticket.dto;

import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.sla.SlaStatus;
import java.time.Instant;
import java.util.List;

public record TicketSummaryResponse(
        Long id,
        String ticketKey,
        String type,
        String status,
        String priority,
        String severity,
        String title,
        String organizationName,
        String businessOwnerName,
        String ticketLeadName,
        List<String> developerNames,
        String currentResponsibility,
        String slaStatus,
        Instant createdAt,
        Instant updatedAt) {

    public static TicketSummaryResponse from(Ticket ticket, SlaStatus slaStatus) {
        return new TicketSummaryResponse(
                ticket.getId(),
                ticket.getTicketKey(),
                ticket.getTicketType().getKey(),
                ticket.getCurrentState().getKey(),
                ticket.getPriority().name(),
                ticket.getSeverity() == null ? null : ticket.getSeverity().name(),
                ticket.getTitle(),
                ticket.getOrganization().getName(),
                ticket.getBusinessOwner().getDisplayName(),
                ticket.getTicketLead() == null ? null : ticket.getTicketLead().getDisplayName(),
                ticket.getDevelopers().stream().map(user -> user.getDisplayName()).sorted().toList(),
                ticket.getCurrentResponsibility().name(),
                slaStatus.name(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }
}
