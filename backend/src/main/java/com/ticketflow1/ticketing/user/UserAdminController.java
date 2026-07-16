package com.ticketflow1.ticketing.user;

import com.ticketflow1.ticketing.common.PagedResponse;
import com.ticketflow1.ticketing.user.dto.CreateUserRequest;
import com.ticketflow1.ticketing.user.dto.UserResponse;
import com.ticketflow1.ticketing.user.dto.UpdateUserRoleRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.ticketflow1.ticketing.auth.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class UserAdminController {

    private final UserService userService;

    public UserAdminController(UserService userService) {
        this.userService = userService;
    }

    /**
     * TicketFlow1-side roles need this to populate the ticket-lead picker —
     * mirrors the same override on OrganizationAdminController.list().
     */
    @GetMapping
    public PagedResponse<UserResponse> list(
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) Long roleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return userService.list(organizationId, roleId, page, pageSize, principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return userService.create(request, principal);
    }

    @PatchMapping("/{userId}/role")
    public UserResponse updateRole(@PathVariable Long userId,
            @Valid @RequestBody UpdateUserRoleRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return userService.updateRoles(userId, request.roleIds(), principal);
    }
}
