package com.ticketflow1.ticketing.ticket;

/**
 * Labels match the Postgres {@code responsibility} enum (V2). Tracks whose
 * court the ball is in; flipped by specific transitions (plan.md diagrams),
 * never set directly by API callers.
 */
public enum Responsibility {
    CLIENT,
    TICKETFLOW1
}
