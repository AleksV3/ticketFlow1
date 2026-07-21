package com.ticketflow1.ticketing.workflow.dto;

import com.ticketflow1.ticketing.workflow.TicketType;
import com.ticketflow1.ticketing.workflow.TicketTypeCapability;

public record TicketTypeAdminResponse(Long id, String key, String name, Long workflowId,
        Long organizationId, boolean isTemplate, boolean requiresProposal, boolean active, int sortOrder,
        TicketTypeCapability capability, long version) {
    public static TicketTypeAdminResponse from(TicketType type) {
        return new TicketTypeAdminResponse(type.getId(), type.getKey(), type.getName(), type.getWorkflow().getId(),
                type.getOrganization() == null ? null : type.getOrganization().getId(), type.isTemplate(), type.isRequiresProposal(),
                type.isActive(), type.getSortOrder(), type.getCapability(), type.getVersion());
    }
}
