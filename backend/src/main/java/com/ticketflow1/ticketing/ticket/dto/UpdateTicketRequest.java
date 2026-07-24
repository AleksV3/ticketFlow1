package com.ticketflow1.ticketing.ticket.dto;

import com.ticketflow1.ticketing.ticket.Priority;
import com.ticketflow1.ticketing.ticket.Severity;
import java.util.Set;
import java.util.Map;

public record UpdateTicketRequest(
        String status,
        String title,
        String description,
        Priority priority,
        Severity severity,
        Long ticketLeadId,
        Set<Long> developerIds,
        String assignedTeam,
        Set<Long> teamIds,
        Map<String,Object> dynamicValues) {
    public UpdateTicketRequest(String status, String title, String description, Priority priority,
            Severity severity, Long ticketLeadId, Set<Long> developerIds, String assignedTeam, Set<Long> teamIds) {
        this(status, title, description, priority, severity, ticketLeadId, developerIds, assignedTeam, teamIds, null);
    }
}
