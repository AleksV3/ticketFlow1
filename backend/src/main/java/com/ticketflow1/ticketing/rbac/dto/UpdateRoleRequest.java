package com.ticketflow1.ticketing.rbac.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

/** Name/permissions use PATCH semantics; version is required for stale-write protection. */
public record UpdateRoleRequest(
        String name,
        Set<String> permissionKeys,
        @NotNull Long version) {
}
