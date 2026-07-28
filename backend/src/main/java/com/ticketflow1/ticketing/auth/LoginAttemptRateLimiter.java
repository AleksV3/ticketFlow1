package com.ticketflow1.ticketing.auth;

import com.ticketflow1.ticketing.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;

/**
 * Limits failed login attempts with PostgreSQL-backed state so every Cloud Run
 * instance enforces the same account throttle.
 */
@Component
class LoginAttemptRateLimiter {

    private final JdbcTemplate jdbcTemplate;
    private final int maxFailures;
    private final Duration window;
    private final Clock clock;

    @Autowired
    LoginAttemptRateLimiter(JdbcTemplate jdbcTemplate,
            @Value("${app.security.login-rate-limit.max-failures:5}") int maxFailures,
            @Value("${app.security.login-rate-limit.window-seconds:900}") long windowSeconds) {
        if (maxFailures < 1 || windowSeconds < 1) {
            throw new IllegalArgumentException("Login rate limit values must be positive.");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.maxFailures = maxFailures;
        this.window = Duration.ofSeconds(windowSeconds);
        this.clock = Clock.systemUTC();
    }

    synchronized void assertAllowed(String email) {
        String key = key(email);
        Integer failures = jdbcTemplate.query("SELECT failures FROM login_rate_limit WHERE email_key=? AND window_started_at>=?",
                rs -> rs.next() ? rs.getInt(1) : null, key, Timestamp.from(clock.instant().minus(window)));
        if (failures != null && failures >= maxFailures) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED",
                    "Too many login attempts. Please try again later.");
        }
    }

    synchronized void recordFailure(String email) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        jdbcTemplate.update("""
                INSERT INTO login_rate_limit (email_key, window_started_at, failures, updated_at)
                VALUES (?, ?, 1, ?)
                ON CONFLICT (email_key) DO UPDATE SET
                  failures = CASE WHEN login_rate_limit.window_started_at < ? THEN 1 ELSE login_rate_limit.failures + 1 END,
                  window_started_at = CASE WHEN login_rate_limit.window_started_at < ? THEN EXCLUDED.window_started_at ELSE login_rate_limit.window_started_at END,
                  updated_at = EXCLUDED.updated_at
                """, key(email), Timestamp.from(now), Timestamp.from(now), Timestamp.from(cutoff), Timestamp.from(cutoff));
    }

    synchronized void recordSuccess(String email) {
        jdbcTemplate.update("DELETE FROM login_rate_limit WHERE email_key=?", key(email));
    }

    private static String key(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
