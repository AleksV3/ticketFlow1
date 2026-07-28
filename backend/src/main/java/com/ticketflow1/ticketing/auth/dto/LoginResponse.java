package com.ticketflow1.ticketing.auth.dto;

import com.ticketflow1.ticketing.ticket.Responsibility;
import java.time.Instant;

public record LoginResponse(Instant expiresAt, UserSummary user, boolean passwordChangeRequired) {

    public record UserSummary(
            Long id,
            String email,
            String displayName,
            String roleName,
            Responsibility party,
            Long organizationId) {
    }
}
