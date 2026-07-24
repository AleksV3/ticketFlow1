package com.ticketflow1.ticketing.rbac.dto;

import com.ticketflow1.ticketing.rbac.Permission;
import com.ticketflow1.ticketing.rbac.Role;
import com.ticketflow1.ticketing.ticket.Responsibility;
import java.util.Set;
import java.util.stream.Collectors;

public record RoleResponse(
        Long id,
        String name,
        Responsibility party,
        Long organizationId,
        boolean template,
        Set<String> permissions,
        long version) {

    public static RoleResponse from(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getParty(),
                role.getOrganization() == null ? null : role.getOrganization().getId(),
                role.isTemplate(),
                role.getPermissions().stream().map(Permission::getKey).collect(Collectors.toSet()),
                role.getVersion());
    }
}
