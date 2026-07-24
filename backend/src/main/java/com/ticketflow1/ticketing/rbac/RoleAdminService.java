package com.ticketflow1.ticketing.rbac;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.organization.OrganizationRepository;
import com.ticketflow1.ticketing.rbac.dto.CreateRoleRequest;
import com.ticketflow1.ticketing.rbac.dto.RoleResponse;
import com.ticketflow1.ticketing.rbac.dto.UpdateRoleRequest;
import com.ticketflow1.ticketing.ticket.Responsibility;
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
    public List<RoleResponse> list(AuthPrincipal principal, Long organizationId) {
        if (principal.party() == Responsibility.CLIENT) organizationId = principal.organizationId();
        List<Role> roles = organizationId == null
                ? roleRepository.findByOrganizationIsNull()
                : roleRepository.findByOrganizationId(organizationId);
        return roles.stream().map(RoleResponse::from).toList();
    }

    @Transactional
    public RoleResponse create(AuthPrincipal principal, CreateRoleRequest request) {
        if (principal.party() == Responsibility.CLIENT
                && (request.party() != Responsibility.CLIENT
                    || !principal.organizationId().equals(request.organizationId()))) {
            throw ApiException.notFound("Organization not found: " + request.organizationId());
        }
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
        role.replacePermissions(resolvePermissions(request.permissionKeys()));
        Role saved = roleRepository.saveAndFlush(role);
        return RoleResponse.from(saved);
    }

    @Transactional
    public RoleResponse update(AuthPrincipal principal, Long roleId, UpdateRoleRequest request) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> ApiException.notFound("Role not found: " + roleId));
        if (principal.party() == Responsibility.CLIENT
                && (role.getOrganization() == null
                    || !principal.organizationId().equals(role.getOrganization().getId()))) {
            throw ApiException.notFound("Role not found: " + roleId);
        }
        if (role.isTemplate()) {
            throw ApiException.validation(
                    "Template roles cannot be edited directly — edit an organization's cloned copy.");
        }
        if (!java.util.Objects.equals(request.version(), role.getVersion())) {
            throw ApiException.conflict(
                    "Role was changed by another administrator. Reload and try again.");
        }
        if (request.name() != null && !request.name().isBlank()) {
            role.setName(request.name());
        }
        if (request.permissionKeys() != null) {
            Set<Permission> replacement = resolvePermissions(request.permissionKeys());
            role.replacePermissions(replacement);
        }
        return RoleResponse.from(roleRepository.saveAndFlush(role));
    }

    private Set<Permission> resolvePermissions(Set<String> keys) {
        Set<Permission> permissions = permissionRepository.findByKeyIn(keys);
        if (permissions.size() != keys.size()) {
            Set<String> resolved = permissions.stream()
                    .map(Permission::getKey)
                    .collect(java.util.stream.Collectors.toSet());
            Set<String> unknown = new java.util.TreeSet<>(keys);
            unknown.removeAll(resolved);
            throw ApiException.validation("Unknown permissions: " + unknown);
        }
        return permissions;
    }
}
