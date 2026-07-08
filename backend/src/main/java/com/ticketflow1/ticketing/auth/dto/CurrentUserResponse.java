package com.ticketflow1.ticketing.auth.dto;

import com.ticketflow1.ticketing.ticket.Responsibility;
import java.util.Set;

public record CurrentUserResponse(
        Long id,
        String email,
        String displayName,
        String roleName,
        Responsibility party,
        Long organizationId,
        String organizationName,
        Set<String> permissions) {
}
