package com.ticketflow1.ticketing.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * roleId picks one row from GET /api/admin/roles (global TicketFlow1-party
 * roles, or — when organizationId is set — that organization's own cloned
 * CLIENT-party roles). party is never supplied directly; it's derived from
 * the chosen role (UserService.validateRoleAndOrganization).
 */
public record CreateUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 6, max = 100) String password,
        @NotBlank @Size(max = 200) String displayName,
        @NotNull Long roleId,
        // Required for CLIENT-party roles, forbidden otherwise — checked in UserService.
        Long organizationId) {
}
