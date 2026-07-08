package com.ticketflow1.ticketing.organization;

import com.ticketflow1.ticketing.organization.dto.CreateOrganizationRequest;
import com.ticketflow1.ticketing.organization.dto.OrganizationResponse;
import com.ticketflow1.ticketing.organization.dto.UpdateOrganizationRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/organizations")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class OrganizationAdminController {

    private final OrganizationService organizationService;

    public OrganizationAdminController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    /**
     * Read access is wider than the class-level gate (method-level
     * @PreAuthorize overrides class-level): TicketFlow1-side roles MUST name an
     * organizationId when creating a ticket (TicketService.resolveOrganization,
     * FR-018), so the create-ticket form needs this list. Mutations below stay
     * gated on USER_MANAGE.
     */
    @GetMapping
    @PreAuthorize("principal.party() == T(com.ticketflow1.ticketing.ticket.Responsibility).TICKETFLOW1")
    public List<OrganizationResponse> list() {
        return organizationService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResponse create(@Valid @RequestBody CreateOrganizationRequest request) {
        return organizationService.create(request);
    }

    @PatchMapping("/{id}")
    public OrganizationResponse update(@PathVariable Long id,
            @Valid @RequestBody UpdateOrganizationRequest request) {
        return organizationService.update(id, request);
    }
}
