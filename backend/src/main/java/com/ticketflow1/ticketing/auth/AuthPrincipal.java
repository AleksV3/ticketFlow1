package com.ticketflow1.ticketing.auth;

import com.ticketflow1.ticketing.ticket.Responsibility;
import java.util.Set;

/**
 * The authenticated caller, reconstructed from JWT claims on every request and
 * stored as the SecurityContext principal. {@code organizationId} is null for
 * TicketFlow1-side users. Authorization checks test {@code permissions}
 * (and, where relevant, {@code party}) — never a role name.
 */
public record AuthPrincipal(Long userId, Responsibility party, Long organizationId, Set<String> permissions,
        boolean passwordChangeRequired) {

    /** Backwards-compatible principal for tests and trusted server-side callers. */
    public AuthPrincipal(Long userId, Responsibility party, Long organizationId, Set<String> permissions) {
        this(userId, party, organizationId, permissions, false);
    }

    public boolean hasPermission(String permissionKey) {
        return permissions.contains(permissionKey);
    }
}
