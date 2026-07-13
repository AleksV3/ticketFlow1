package com.ticketflow1.ticketing.ticket.dto;

import com.ticketflow1.ticketing.ticket.Priority;
import com.ticketflow1.ticketing.ticket.Severity;

public record UpdateTicketRequest(
        String status,
        String title,
        String description,
        Priority priority,
        Severity severity,
        Long ticketLeadId,
        String assignedTeam) {
}
