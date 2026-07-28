package com.ticketflow1.ticketing.auth;

import com.ticketflow1.ticketing.auth.dto.CurrentUserResponse;
import com.ticketflow1.ticketing.auth.dto.ChangePasswordRequest;
import com.ticketflow1.ticketing.auth.dto.LoginRequest;
import com.ticketflow1.ticketing.auth.dto.LoginResponse;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.rbac.Permission;
import com.ticketflow1.ticketing.user.AppUser;
import com.ticketflow1.ticketing.user.AppUserRepository;
import org.springframework.http.ResponseCookie;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResult login(LoginRequest request) {
        // Same 401 message for every failure mode (unknown email, wrong password,
        // inactive account, deactivated org) so we never leak which one failed.
        AppUser user = userRepository.findByEmail(request.email())
                .orElseThrow(AuthService::invalidCredentials);

        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        Organization org = user.getOrganization();
        if (org != null && !org.isActive()) {
            throw invalidCredentials();
        }

        JwtService.IssuedToken issued = jwtService.issue(user);
        Long orgId = org == null ? null : org.getId();
        var summary = new LoginResponse.UserSummary(
                user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRole().getName(), user.getParty(), orgId);
        return new LoginResult(
                new LoginResponse(issued.expiresAt(), summary, user.isMustChangePassword()),
                jwtService.buildAuthCookie(issued.token()));
    }

    public ResponseCookie clearLoginCookie() {
        return jwtService.clearAuthCookie();
    }

    @Transactional(readOnly = true)
    public CurrentUserResult currentUser(AuthPrincipal principal) {
        AppUser user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("Current user no longer exists."));
        Organization org = user.getOrganization();
        Set<String> permissions = user.getRoles().stream().flatMap(role -> role.getPermissions().stream())
                .map(Permission::getKey)
                .collect(Collectors.toSet());
        var response = new CurrentUserResponse(
                user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRole().getName(), user.getParty(),
                org == null ? null : org.getId(),
                org == null ? null : org.getName(),
                permissions, user.isMustChangePassword());
        ResponseCookie refreshedCookie = null;
        if (!permissions.equals(principal.permissions())
                || principal.passwordChangeRequired() != user.isMustChangePassword()) {
            JwtService.IssuedToken issued = jwtService.issue(user);
            refreshedCookie = jwtService.buildAuthCookie(issued.token());
        }
        return new CurrentUserResult(response, refreshedCookie);
    }

    @Transactional
    public LoginResult changePassword(AuthPrincipal principal, ChangePasswordRequest request) {
        AppUser user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("Current user no longer exists."));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw ApiException.validation("Choose a new password that is different from the current password.");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        AppUser saved = userRepository.saveAndFlush(user);
        JwtService.IssuedToken issued = jwtService.issue(saved);
        Organization org = saved.getOrganization();
        var summary = new LoginResponse.UserSummary(saved.getId(), saved.getEmail(), saved.getDisplayName(),
                saved.getRole().getName(), saved.getParty(), org == null ? null : org.getId());
        return new LoginResult(new LoginResponse(issued.expiresAt(), summary, false),
                jwtService.buildAuthCookie(issued.token()));
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Invalid email or password.");
    }

    public record LoginResult(LoginResponse response, ResponseCookie cookie) {
    }

    /**
     * The current-user endpoint reads permissions from the database. When they
     * differ from the JWT claims, return a refreshed cookie so subsequent
     * authorization checks immediately use the current role configuration.
     */
    public record CurrentUserResult(CurrentUserResponse response, ResponseCookie refreshedCookie) {
    }
}
