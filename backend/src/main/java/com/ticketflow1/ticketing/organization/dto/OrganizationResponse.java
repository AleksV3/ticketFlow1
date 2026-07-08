package com.ticketflow1.ticketing.organization.dto;

import com.ticketflow1.ticketing.organization.Organization;
import java.time.Instant;

public record OrganizationResponse(Long id, String name, boolean active, Instant createdAt) {

    public static OrganizationResponse from(Organization org) {
        return new OrganizationResponse(org.getId(), org.getName(), org.isActive(), org.getCreatedAt());
    }
}
