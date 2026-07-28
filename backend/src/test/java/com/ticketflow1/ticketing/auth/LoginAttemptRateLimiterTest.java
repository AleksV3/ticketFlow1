package com.ticketflow1.ticketing.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketflow1.ticketing.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptRateLimiterTest {

    @Test
    void blocksTheSixthFailedAttemptForTheSameEmail() {
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(5, 900,
                Clock.fixed(Instant.parse("2026-07-28T10:00:00Z"), ZoneOffset.UTC));

        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.assertAllowed("user@example.test");
            limiter.recordFailure("user@example.test");
        }

        assertThatThrownBy(() -> limiter.assertAllowed("USER@example.test"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Too many login attempts. Please try again later.");
    }

    @Test
    void clearsFailuresAfterASuccessfulLogin() {
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(1, 900, Clock.systemUTC());
        limiter.recordFailure("user@example.test");
        limiter.recordSuccess("user@example.test");

        limiter.assertAllowed("user@example.test");
    }
}
