package com.ticketflow1.ticketing.ticket.dto;

import com.ticketflow1.ticketing.ticket.Priority;
import com.ticketflow1.ticketing.ticket.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.Map;

public record CreateTicketRequest(
        @NotBlank String type,
        @NotBlank @Size(max = 300) String title,
        @NotBlank String description,
        @NotNull Priority priority,
        Severity severity,
        Long organizationId,
        Long ticketLeadId,
        Set<Long> developerIds,
        Set<Long> teamIds,
        Long subtypeId,
        Map<String,Object> dynamicValues,
        String parentTicketKey,
        Long targetUserId) {
}
