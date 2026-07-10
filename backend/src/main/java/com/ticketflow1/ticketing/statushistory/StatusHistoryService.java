package com.ticketflow1.ticketing.statushistory;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.statushistory.dto.StatusHistoryResponse;
import com.ticketflow1.ticketing.ticket.Responsibility;
import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.ticket.TicketRepository;
import com.ticketflow1.ticketing.user.AppUserRepository;
import com.ticketflow1.ticketing.workflow.WorkflowState;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatusHistoryService {

    private final StatusHistoryRepository statusHistoryRepository;
    private final AppUserRepository appUserRepository;
    private final TicketRepository ticketRepository;

    public StatusHistoryService(StatusHistoryRepository statusHistoryRepository,
            AppUserRepository appUserRepository,
            TicketRepository ticketRepository) {
        this.statusHistoryRepository = statusHistoryRepository;
        this.appUserRepository = appUserRepository;
        this.ticketRepository = ticketRepository;
    }

    @Transactional
    public StatusHistory record(Ticket ticket, WorkflowState fromState, WorkflowState toState, Long changedById) {
        StatusHistory statusHistory = new StatusHistory(
                ticket,
                fromState,
                toState,
                appUserRepository.getReferenceById(changedById));
        return statusHistoryRepository.save(statusHistory);
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> list(String ticketKey, AuthPrincipal principal) {
        Ticket ticket = findVisibleTicket(ticketKey, principal);
        return statusHistoryRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()).stream()
                .map(StatusHistoryResponse::from)
                .toList();
    }

    private Ticket findVisibleTicket(String ticketKey, AuthPrincipal principal) {
        if (principal.party() == Responsibility.CLIENT) {
            return ticketRepository.findByTicketKeyAndOrganizationId(ticketKey, principal.organizationId())
                    .orElseThrow(() -> ApiException.notFound("Ticket not found: " + ticketKey));
        }
        return ticketRepository.findByTicketKey(ticketKey)
                .orElseThrow(() -> ApiException.notFound("Ticket not found: " + ticketKey));
    }
}
