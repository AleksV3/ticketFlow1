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

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-hours}") long expirationHours) {
        // HS256 requires a key of at least 256 bits (32 bytes).
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofHours(expirationHours);
    }

    public IssuedToken issue(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expiration);
        String permissions = user.getRole().getPermissions().stream()
                .map(Permission::getKey)
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
