package com.ticketflow1.ticketing.auth;

import com.ticketflow1.ticketing.auth.dto.CurrentUserResponse;
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
                new LoginResponse(issued.expiresAt(), summary),
                jwtService.buildAuthCookie(issued.token()));
    }

    public ResponseCookie clearLoginCookie() {
        return jwtService.clearAuthCookie();
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(AuthPrincipal principal) {
        AppUser user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("Current user no longer exists."));
        Organization org = user.getOrganization();
        Set<String> permissions = user.getRoles().stream().flatMap(role -> role.getPermissions().stream())
                .map(Permission::getKey)
                .collect(Collectors.toSet());
        return new CurrentUserResponse(
                user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRole().getName(), user.getParty(),
                org == null ? null : org.getId(),
                org == null ? null : org.getName(),
                permissions);
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Invalid email or password.");
    }

    public record LoginResult(LoginResponse response, ResponseCookie cookie) {
    }
}
