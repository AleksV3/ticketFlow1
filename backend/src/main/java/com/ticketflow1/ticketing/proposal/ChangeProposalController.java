package com.ticketflow1.ticketing.proposal;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.proposal.dto.ChangeProposalResponse;
import com.ticketflow1.ticketing.proposal.dto.CreateProposalRequest;
import com.ticketflow1.ticketing.proposal.dto.ProposalDecisionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ChangeProposalController {
    private final ChangeProposalService service;
    public ChangeProposalController(ChangeProposalService service) { this.service = service; }

    @PostMapping("/api/tickets/{ticketKey}/proposals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TICKET_TRANSITION')")
    public ChangeProposalResponse create(@PathVariable String ticketKey, @Valid @RequestBody CreateProposalRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) { return service.create(ticketKey, request, principal); }

    @PostMapping("/api/proposals/{id}/approve")
    @PreAuthorize("hasAuthority('PROPOSAL_APPROVE')")
    public ChangeProposalResponse approve(@PathVariable Long id, @RequestBody(required = false) ProposalDecisionRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.approve(id, request == null ? null : request.comment(), principal);
    }

    @PostMapping("/api/proposals/{id}/reject")
    @PreAuthorize("hasAuthority('PROPOSAL_APPROVE')")
    public ChangeProposalResponse reject(@PathVariable Long id, @RequestBody ProposalDecisionRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) { return service.reject(id, request.comment(), principal); }
}
