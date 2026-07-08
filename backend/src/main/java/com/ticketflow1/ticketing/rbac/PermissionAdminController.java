package com.ticketflow1.ticketing.rbac;

import com.ticketflow1.ticketing.rbac.dto.PermissionResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The fixed, code-owned catalog (FR-008) — read-only, used to build role permission pickers. */
@RestController
@RequestMapping("/api/admin/permissions")
@PreAuthorize("hasAuthority('ROLE_MANAGE')")
public class PermissionAdminController {

    private final PermissionRepository permissionRepository;

    public PermissionAdminController(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @GetMapping
    public List<PermissionResponse> list() {
        return permissionRepository.findAll().stream().map(PermissionResponse::from).toList();
    }
}
