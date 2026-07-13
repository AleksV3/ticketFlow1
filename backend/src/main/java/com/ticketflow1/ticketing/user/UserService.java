package com.ticketflow1.ticketing.user;

import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.common.PagedResponse;
import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.organization.OrganizationRepository;
import com.ticketflow1.ticketing.rbac.Role;
import com.ticketflow1.ticketing.rbac.RoleRepository;
import com.ticketflow1.ticketing.ticket.Responsibility;
import com.ticketflow1.ticketing.user.dto.CreateUserRequest;
import com.ticketflow1.ticketing.user.dto.UserResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final AppUserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository userRepository,
            OrganizationRepository organizationRepository, RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Org-required-for-CLIENT-roles rule (data-model.md / FR-010): a CLIENT
     * party role must belong to the SAME organization being assigned (it's
     * that organization's own cloned role, not the global template); a
     * TICKETFLOW1 party role must have no organization at all. Enforced here
     * (not as a DB CHECK) so the caller gets a clear 400 message.
     */
    public void validateRoleAndOrganization(Role role, Long organizationId) {
        if (role.getParty() == Responsibility.CLIENT) {
            if (organizationId == null || role.getOrganization() == null
                    || !role.getOrganization().getId().equals(organizationId)) {
                throw ApiException.validation(
                        "Role '" + role.getName() + "' belongs to a specific organization; "
                                + "organizationId must match it.");
            }
        } else if (organizationId != null) {
            throw ApiException.validation(
                    "organizationId must not be set for role '" + role.getName()
                            + "' (TicketFlow1-side roles work across all organizations)");
        }
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, AuthPrincipal principal) {
        if (principal.party() == Responsibility.CLIENT
                && !principal.organizationId().equals(request.organizationId())) {
            throw ApiException.notFound("Organization not found: " + request.organizationId());
        }
        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> ApiException.validation("Role not found: " + request.roleId()));
        validateRoleAndOrganization(role, request.organizationId());
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.validation("A user with email '" + request.email() + "' already exists.");
        }

        Organization org = null;
        if (request.organizationId() != null) {
            org = organizationRepository.findById(request.organizationId())
                    .orElseThrow(() -> ApiException.validation(
                            "Organization not found: " + request.organizationId()));
        }

        AppUser user = new AppUser(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.displayName(),
                role.getParty(),
                role,
                org);
        // saveAndFlush so @CreationTimestamp is set before mapping the response.
        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> list(Long organizationId, Long roleId, int page, int pageSize,
            AuthPrincipal principal) {
        Long scopedOrganizationId = principal.party() == Responsibility.CLIENT
                ? principal.organizationId() : organizationId;
        int size = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int pageNumber = Math.max(page, 0);
        Pageable pageable = PageRequest.of(pageNumber, size, Sort.by("createdAt").descending());

        Specification<AppUser> spec = Specification.where(null);
        if (scopedOrganizationId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("organization").get("id"), scopedOrganizationId));
        }
        if (roleId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role").get("id"), roleId));
        }
        return PagedResponse.from(userRepository.findAll(spec, pageable), UserResponse::from);
    }
}
