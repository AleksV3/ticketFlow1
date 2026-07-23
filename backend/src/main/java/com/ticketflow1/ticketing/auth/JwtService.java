package com.ticketflow1.ticketing.auth;

import com.ticketflow1.ticketing.rbac.Permission;
import com.ticketflow1.ticketing.user.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * Issues and parses HS256 JWTs. The token is the only thing the server needs to
 * know who the caller is — no server-side session store (research.md, FR-015).
 * Permissions are resolved from the user's role and embedded in the token
 * itself (not looked up per request) — same stateless-token design as before,
 * just with a permission set instead of a single role claim.
 */
@Service
public class JwtService {

    public static final String AUTH_COOKIE_NAME = "ticketflow1_auth";

    private final SecretKey key;
    private final Duration expiration;
    private final boolean secureCookies;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-hours}") long expirationHours,
            @Value("${app.security.secure-cookies}") boolean secureCookies) {
        // HS256 requires a key of at least 256 bits (32 bytes).
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofHours(expirationHours);
        this.secureCookies = secureCookies;
    }

    public IssuedToken issue(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expiration);
        String permissions = user.getRoles().stream().flatMap(role -> role.getPermissions().stream())
                .map(Permission::getKey)
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
        String token = Jwts.builder()
                .subject(user.getId().toString())
                .claim("party", user.getParty().name())
                .claim("orgId", user.getOrganization() == null
                        ? null : user.getOrganization().getId().toString())
                .claim("permissions", permissions)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    public ResponseCookie buildAuthCookie(String token) {
        return ResponseCookie.from(AUTH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite(sameSite())
                .path("/")
                .maxAge(expiration)
                .build();
    }

    public ResponseCookie clearAuthCookie() {
        return ResponseCookie.from(AUTH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite(sameSite())
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }

    private String sameSite() {
        // Local development uses same-site localhost requests and can stay Lax.
        // Public deployments serve the frontend and backend from different
        // HTTPS domains, so browsers require SameSite=None for the auth cookie
        // to be stored and sent on cross-site fetch requests.
        return secureCookies ? "None" : "Lax";
    }

    /** Parses and verifies the signature/expiry. Throws JwtException if invalid. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
