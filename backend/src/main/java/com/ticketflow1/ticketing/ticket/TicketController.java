package com.ticketflow1.ticketing.ticket;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.PagedResponse;
import com.ticketflow1.ticketing.ticket.dto.CreateTicketRequest;
import com.ticketflow1.ticketing.ticket.dto.TicketDetailResponse;
import com.ticketflow1.ticketing.ticket.dto.TicketSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TICKET_READ')")
    public PagedResponse<TicketSummaryResponse> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) Responsibility responsibility,
            @RequestParam(required = false) String slaStatus,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ticketService.listTickets(type, status, severity, priority, assignedTo, responsibility,
                slaStatus, organizationId, q, page, pageSize, principal);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TICKET_CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketDetailResponse create(@Valid @RequestBody CreateTicketRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ticketService.createTicket(request, principal);
    }

    @GetMapping("/{ticketKey}")
    @PreAuthorize("hasAuthority('TICKET_READ')")
    public TicketDetailResponse getByTicketKey(@PathVariable String ticketKey,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ticketService.getTicket(ticketKey, principal);
    }
}
