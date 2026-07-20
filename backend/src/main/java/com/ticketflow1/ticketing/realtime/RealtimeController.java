package com.ticketflow1.ticketing.realtime;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/events")
public class RealtimeController {
    private final RealtimeEvents events;
    public RealtimeController(RealtimeEvents events) { this.events = events; }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('TICKET_READ')")
    public SseEmitter subscribe() { return events.subscribe(); }
}
