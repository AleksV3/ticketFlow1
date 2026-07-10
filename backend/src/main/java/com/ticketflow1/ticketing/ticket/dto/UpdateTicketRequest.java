package com.ticketflow1.ticketing.ticket.dto;

import com.ticketflow1.ticketing.ticket.Priority;

public record UpdateTicketRequest(
        String status,
        String title,
        String description,
        Priority priority,
        Long ticketLeadId,
        String assignedTeam) {
}
