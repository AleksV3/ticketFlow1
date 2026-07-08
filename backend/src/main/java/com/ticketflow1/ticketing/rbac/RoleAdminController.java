package com.ticketflow1.ticketing.rbac;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.rbac.dto.CreateRoleRequest;
import com.ticketflow1.ticketing.rbac.dto.RoleResponse;
import com.ticketflow1.ticketing.rbac.dto.UpdateRoleRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/roles")
@PreAuthorize("hasAuthority('ROLE_MANAGE')")
public class RoleAdminController {

    private final RoleAdminService roleAdminService;

    public RoleAdminController(RoleAdminService roleAdminService) {
        this.roleAdminService = roleAdminService;
    }

    /** No organizationId: the global TICKETFLOW1-party role templates. */
    @GetMapping
    public List<RoleResponse> list(@RequestParam(required = false) Long organizationId) {
        return roleAdminService.list(organizationId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse create(@AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateRoleRequest request) {
        return roleAdminService.create(principal, request);
    }

    @PatchMapping("/{id}")
    public RoleResponse update(@AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long id, @RequestBody UpdateRoleRequest request) {
        return roleAdminService.update(principal, id, request);
    }
}
