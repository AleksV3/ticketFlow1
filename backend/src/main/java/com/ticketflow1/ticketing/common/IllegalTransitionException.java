package com.ticketflow1.ticketing.common;

import org.springframework.http.HttpStatus;

/**
 * A status transition not allowed for the ticket's type + current status +
 * caller's role. Mapped to 409 ILLEGAL_TRANSITION by ApiExceptionHandler
 * (contracts/README.md) — 409, not 403, because the conflict is with the
 * ticket's state machine, not merely the caller's identity.
 */
public class IllegalTransitionException extends ApiException {

    public IllegalTransitionException(String message) {
        super(HttpStatus.CONFLICT, "ILLEGAL_TRANSITION", message);
    }
}
