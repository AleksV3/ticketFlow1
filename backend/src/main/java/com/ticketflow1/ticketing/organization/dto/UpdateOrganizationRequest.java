package com.ticketflow1.ticketing.organization.dto;

import jakarta.validation.constraints.Size;

/** Both fields optional (PATCH semantics): null means "leave unchanged". */
public record UpdateOrganizationRequest(
        @Size(max = 200) String name,
        Boolean active) {
}
