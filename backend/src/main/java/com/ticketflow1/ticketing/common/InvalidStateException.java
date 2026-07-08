package com.ticketflow1.ticketing.common;

import org.springframework.http.HttpStatus;

/**
 * The resource's own state forbids the operation — e.g. approving a proposal
 * that's already decided, or creating one on a ticket that isn't in ANALYSIS.
 * 409 INVALID_STATE (contracts/README.md), distinct from ILLEGAL_TRANSITION:
 * that one is about the ticket status graph, this one about everything else.
 */
public class InvalidStateException extends ApiException {

    public InvalidStateException(String message) {
        super(HttpStatus.CONFLICT, "INVALID_STATE", message);
    }
}
