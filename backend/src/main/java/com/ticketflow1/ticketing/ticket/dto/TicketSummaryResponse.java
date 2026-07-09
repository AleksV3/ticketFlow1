package com.ticketflow1.ticketing.ticket.dto;

import com.ticketflow1.ticketing.ticket.Ticket;
import java.time.Instant;

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
        String currentResponsibility,
        String slaStatus,
        Instant createdAt,
        Instant updatedAt) {

    public static TicketSummaryResponse from(Ticket ticket) {
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
                ticket.getCurrentResponsibility().name(),
                null,
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }
}
