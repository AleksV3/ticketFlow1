package com.ticketflow1.ticketing.rbac.dto;

import com.ticketflow1.ticketing.ticket.Responsibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * A new configurable role (FR-009). organizationId is required for CLIENT
 * party (the role belongs to that organization only) and forbidden for
 * TICKETFLOW1 party (global, works across all organizations) — checked in
 * RoleAdminService, same rule as AppUser's party/organization pairing.
 */
public record CreateRoleRequest(
        @NotBlank String name,
        @NotNull Responsibility party,
        Long organizationId,
        @NotNull Set<String> permissionKeys) {
}
