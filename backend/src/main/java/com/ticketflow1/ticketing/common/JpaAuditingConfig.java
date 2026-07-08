package com.ticketflow1.ticketing.common;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Answers "who is the current user?" for {@code @LastModifiedBy}. Reads the
 * AuthPrincipal that JwtAuthFilter put into the SecurityContext, so the stamp
 * is written inside the same transaction as the business change. This lives in
 * the app layer, not a DB trigger, because the database connection is a shared
 * pool identity — Postgres has no idea which person is logged in.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<Long> auditorProvider() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
                return Optional.of(principal.userId());
            }
            // Unauthenticated writes (Flyway seeds, bootstrap) stamp null = system.
            return Optional.empty();
        };
    }
}
