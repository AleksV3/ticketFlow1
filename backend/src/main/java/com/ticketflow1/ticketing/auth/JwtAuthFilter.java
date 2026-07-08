package com.ticketflow1.ticketing.auth;

import com.ticketflow1.ticketing.ticket.Responsibility;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs once per request. If a valid Bearer token is present, it populates the
 * SecurityContext with the caller's identity and one GrantedAuthority per
 * permission key (no "ROLE_" prefix — @PreAuthorize checks hasAuthority(...),
 * never a role name). If the token is missing or invalid, it does nothing —
 * the authorization rules downstream decide whether that's allowed.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.parse(token);
                AuthPrincipal principal = toPrincipal(claims);
                List<SimpleGrantedAuthority> authorities = principal.permissions().stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                // Invalid/expired token: leave context empty. Protected endpoints
                // will be rejected by the authentication entry point (401).
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private AuthPrincipal toPrincipal(Claims claims) {
        Long userId = Long.valueOf(claims.getSubject());
        Responsibility party = Responsibility.valueOf(claims.get("party", String.class));
        String orgIdClaim = claims.get("orgId", String.class);
        Long organizationId = orgIdClaim == null ? null : Long.valueOf(orgIdClaim);
        String permissionsClaim = claims.get("permissions", String.class);
        Set<String> permissions = permissionsClaim == null || permissionsClaim.isBlank()
                ? Set.of() : Set.of(permissionsClaim.split(","));
        return new AuthPrincipal(userId, party, organizationId, permissions);
    }
}
