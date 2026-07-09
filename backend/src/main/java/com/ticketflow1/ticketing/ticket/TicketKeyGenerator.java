package com.ticketflow1.ticketing.ticket;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TicketKeyGenerator {

    private final JdbcTemplate jdbcTemplate;

    public TicketKeyGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String nextKey() {
        Long nextValue = jdbcTemplate.queryForObject("select nextval('ticket_key_seq')", Long.class);
        if (nextValue == null) {
            throw new IllegalStateException("ticket_key_seq did not return a value.");
        }
        return "TF-" + nextValue;
    }
}
