package com.ticketflow1.ticketing.user.dto;

import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.ticket.Responsibility;
import com.ticketflow1.ticketing.user.AppUser;
import java.time.Instant;
import java.util.List;

public record UserResponse(
        Long id,
        String email,
        String displayName,
        Responsibility party,
        Long roleId,
        String roleName,
        List<Long> roleIds,
        List<String> roleNames,
        Long organizationId,
        String organizationName,
        boolean active,
        Instant createdAt) {

    public static UserResponse from(AppUser user) {
        Organization org = user.getOrganization();
        return new UserResponse(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getParty(),
                user.getRole().getId(), user.getRole().getName(),
                user.getRoles().stream().map(role -> role.getId()).sorted().toList(),
                user.getRoles().stream().map(role -> role.getName()).sorted().toList(),
                org == null ? null : org.getId(),
                org == null ? null : org.getName(),
                user.isActive(), user.getCreatedAt());
    }
}
