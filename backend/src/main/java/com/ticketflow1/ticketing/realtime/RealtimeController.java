package com.ticketflow1.ticketing.realtime;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
@RequestMapping("/api/events")
public class RealtimeController {
    private final RealtimeEvents events;
    public RealtimeController(RealtimeEvents events) { this.events = events; }

    @GetMapping
    @PreAuthorize("hasAuthority('TICKET_READ')")
    public DeferredResult<ResponseEntity<Void>> subscribe() { return events.subscribe(); }
}
