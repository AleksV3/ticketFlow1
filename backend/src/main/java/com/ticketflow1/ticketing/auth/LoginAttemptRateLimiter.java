package com.ticketflow1.ticketing.auth;

import com.ticketflow1.ticketing.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Limits failed login attempts for one email address without storing passwords,
 * IP addresses, or login history in the database. This protects an account
 * from online password guessing while preserving the same login error for
 * unknown and known email addresses.
 */
@Component
class LoginAttemptRateLimiter {

    private final Map<String, Deque<Instant>> failuresByEmail = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration window;
    private final Clock clock;

    @Autowired
    LoginAttemptRateLimiter(@Value("${app.security.login-rate-limit.max-failures:5}") int maxFailures,
            @Value("${app.security.login-rate-limit.window-seconds:900}") long windowSeconds) {
        this(maxFailures, windowSeconds, Clock.systemUTC());
    }

    LoginAttemptRateLimiter(int maxFailures, long windowSeconds, Clock clock) {
        if (maxFailures < 1 || windowSeconds < 1) {
            throw new IllegalArgumentException("Login rate limit values must be positive.");
        }
        this.maxFailures = maxFailures;
        this.window = Duration.ofSeconds(windowSeconds);
        this.clock = clock;
    }

    synchronized void assertAllowed(String email) {
        String key = key(email);
        Deque<Instant> failures = failuresByEmail.get(key);
        if (failures == null) {
            return;
        }
        pruneExpired(failures);
        if (failures.isEmpty()) {
            failuresByEmail.remove(key);
            return;
        }
        if (failures.size() >= maxFailures) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED",
                    "Too many login attempts. Please try again later.");
        }
    }

    synchronized void recordFailure(String email) {
        Deque<Instant> failures = failuresByEmail.computeIfAbsent(key(email), ignored -> new ArrayDeque<>());
        pruneExpired(failures);
        failures.addLast(clock.instant());
    }

    synchronized void recordSuccess(String email) {
        failuresByEmail.remove(key(email));
    }

    private void pruneExpired(Deque<Instant> failures) {
        Instant cutoff = clock.instant().minus(window);
        while (!failures.isEmpty() && !failures.peekFirst().isAfter(cutoff)) {
            failures.removeFirst();
        }
    }

    private static String key(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
