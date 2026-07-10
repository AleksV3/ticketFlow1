package com.ticketflow1.ticketing.auth.dto;

import com.ticketflow1.ticketing.ticket.Responsibility;
import java.time.Instant;

public record LoginResponse(Instant expiresAt, UserSummary user) {

    public record UserSummary(
            Long id,
            String email,
            String displayName,
            String roleName,
            Responsibility party,
            Long organizationId) {
    }
}
