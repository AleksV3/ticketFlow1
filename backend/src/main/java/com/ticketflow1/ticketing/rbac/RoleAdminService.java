package com.ticketflow1.ticketing.rbac;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.organization.OrganizationRepository;
import com.ticketflow1.ticketing.rbac.dto.CreateRoleRequest;
import com.ticketflow1.ticketing.rbac.dto.RoleResponse;
import com.ticketflow1.ticketing.rbac.dto.UpdateRoleRequest;
import com.ticketflow1.ticketing.ticket.Responsibility;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin surface over roles (FR-009, SC-009): an org admin can add a role —
 * any bundle of permissions from the fixed catalog — with no code change.
 */
@Service
public class RoleAdminService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final OrganizationRepository organizationRepository;

    public RoleAdminService(RoleRepository roleRepository, PermissionRepository permissionRepository,
            OrganizationRepository organizationRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.organizationRepository = organizationRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list(Long organizationId) {
        List<Role> roles = organizationId == null
                ? roleRepository.findByTemplateTrue()
                : roleRepository.findByOrganizationId(organizationId);
        return roles.stream().map(RoleResponse::from).toList();
    }

    @Transactional
    public RoleResponse create(AuthPrincipal principal, CreateRoleRequest request) {
        if (request.party() == Responsibility.CLIENT) {
            if (request.organizationId() == null) {
                throw ApiException.validation("organizationId is required for a CLIENT-party role.");
            }
        } else if (request.organizationId() != null) {
            throw ApiException.validation(
                    "organizationId must not be set for a TICKETFLOW1-party role (global, all organizations).");
        }
        Organization organization = request.organizationId() == null ? null
                : organizationRepository.findById(request.organizationId())
                        .orElseThrow(() -> ApiException.validation(
                                "Organization not found: " + request.organizationId()));

        Role role = new Role(request.name(), request.party(), organization, false);
        role.getPermissions().addAll(resolvePermissions(request.permissionKeys()));
        Role saved = roleRepository.saveAndFlush(role);
        return RoleResponse.from(saved);
    }

    @Transactional
    public RoleResponse update(AuthPrincipal principal, Long roleId, UpdateRoleRequest request) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> ApiException.notFound("Role not found: " + roleId));
        if (role.isTemplate()) {
            throw ApiException.validation(
                    "Template roles cannot be edited directly — edit an organization's cloned copy.");
        }
        if (request.name() != null && !request.name().isBlank()) {
            role.setName(request.name());
        }
        if (request.permissionKeys() != null) {
            role.getPermissions().clear();
            role.getPermissions().addAll(resolvePermissions(request.permissionKeys()));
            // Join-table writes alone don't dirty the role row — force the
            // UPDATE so updated_at/updated_by_id reflect this change.
            role.touchForAudit();
        }
        return RoleResponse.from(role);
    }

    private Set<Permission> resolvePermissions(Set<String> keys) {
        Set<Permission> permissions = new HashSet<>();
        for (String key : keys) {
            permissions.add(permissionRepository.findByKey(key)
                    .orElseThrow(() -> ApiException.validation("Unknown permission: " + key)));
        }
        return permissions;
    }
}
