package com.ticketflow1.ticketing.ticket;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.PagedResponse;
import com.ticketflow1.ticketing.ticket.dto.CreateTicketRequest;
import com.ticketflow1.ticketing.ticket.dto.CorrectionReturnRequest;
import com.ticketflow1.ticketing.ticket.dto.TicketDetailResponse;
import com.ticketflow1.ticketing.ticket.dto.TicketSummaryResponse;
import com.ticketflow1.ticketing.ticket.dto.TransitionTicketRequest;
import com.ticketflow1.ticketing.ticket.dto.UpdateTicketRequest;
import com.ticketflow1.ticketing.workflow.TicketTransitionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
/**
 * Main ticket API.
 *
 * The controller keeps HTTP concerns thin and delegates listing, ticket
 * lifecycle changes, and workflow transitions to the domain services.
 */
public class TicketController {

    private final TicketService ticketService;
    private final TicketTransitionService ticketTransitionService;

    public TicketController(TicketService ticketService, TicketTransitionService ticketTransitionService) {
        this.ticketService = ticketService;
        this.ticketTransitionService = ticketTransitionService;
    }

    /**
     * Lists tickets with optional filters and tenant scoping.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('TICKET_READ')")
    public PagedResponse<TicketSummaryResponse> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String lifecycle,
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
        return ticketService.listTickets(type, status, lifecycle, severity, priority, assignedTo, responsibility,
                slaStatus, organizationId, q, page, pageSize, principal);
    }

    /**
     * Creates a new ticket in the caller's allowed scope.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('TICKET_CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketDetailResponse create(@Valid @RequestBody CreateTicketRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ticketService.createTicket(request, principal);
    }

    /**
     * Returns the detailed view for one ticket.
     */
    @GetMapping("/{ticketKey}")
    @PreAuthorize("hasAuthority('TICKET_READ')")
    public TicketDetailResponse getByTicketKey(@PathVariable String ticketKey,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ticketService.getTicket(ticketKey, principal);
    }

    /**
     * Updates editable ticket fields or assignment data.
     */
    @PatchMapping("/{ticketKey}")
    @PreAuthorize("hasAnyAuthority('TICKET_UPDATE', 'TICKET_ASSIGN')")
    public TicketDetailResponse update(@PathVariable String ticketKey,
            @RequestBody UpdateTicketRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ticketService.updateTicket(ticketKey, request, principal);
    }

    /**
     * Moves the ticket along its configured workflow.
     */
    @PostMapping("/{ticketKey}/transition")
    @PreAuthorize("hasAuthority('TICKET_TRANSITION')")
    public TicketDetailResponse transition(@PathVariable String ticketKey,
            @Valid @RequestBody TransitionTicketRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ticketTransitionService.transition(ticketKey, request.toStatus(), request.comment(), principal);
    }

    @PostMapping("/{ticketKey}/correction-return")
    @PreAuthorize("hasAuthority('TICKET_TRANSITION')")
    public TicketDetailResponse correctionReturn(@PathVariable String ticketKey,
            @Valid @RequestBody CorrectionReturnRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ticketTransitionService.correctionReturn(ticketKey, request.reason(), principal);
    }
}
