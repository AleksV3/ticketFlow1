package com.ticketflow1.ticketing.dashboard;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.sla.SlaCalculator;
import com.ticketflow1.ticketing.sla.SlaSpecifications;
import com.ticketflow1.ticketing.sla.SlaStatus;
import com.ticketflow1.ticketing.sla.SlaStatusService;
import com.ticketflow1.ticketing.ticket.Responsibility;
import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.ticket.TicketRepository;
import com.ticketflow1.ticketing.ticket.dto.TicketSummaryResponse;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the dashboard aggregates used by the landing page.
 *
 * It scopes tickets to the current tenant, computes summary counts, and
 * prepares the spotlight cards for SLA breaches, due-soon items, proposals,
 * confirmations, and ticket assignments.
 */
@Service
public class DashboardService {

    private static final int CARD_LIMIT = 20;
    private final TicketRepository ticketRepository;
    private final SlaCalculator slaCalculator;
    private final SlaStatusService slaStatusService;
    private final Clock clock;

    public DashboardService(TicketRepository ticketRepository, SlaCalculator slaCalculator,
            SlaStatusService slaStatusService, Clock clock) {
        this.ticketRepository = ticketRepository;
        this.slaCalculator = slaCalculator;
        this.slaStatusService = slaStatusService;
        this.clock = clock;
    }

    /**
     * Returns the dashboard snapshot for the current user.
     */
    @Transactional(readOnly = true)
    public DashboardResponse get(AuthPrincipal principal) {
        Specification<Ticket> scope = tenantScope(principal);
        List<Ticket> tickets = ticketRepository.findAll(scope);
        Comparator<Ticket> newestFirst = Comparator.comparing(Ticket::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder()));

        long active = tickets.stream().filter(ticket -> !ticket.getCurrentState().isTerminal()).count();
        long closed = tickets.size() - active;
        Map<String, Long> byType = countBy(tickets, ticket -> ticket.getTicketType().getKey());
        Map<String, Long> byStatus = countBy(tickets, ticket -> ticket.getCurrentState().getKey());
        Map<String, Long> bySeverity = countBy(
                tickets.stream().filter(ticket -> ticket.getSeverity() != null).toList(),
                ticket -> ticket.getSeverity().name());

        return new DashboardResponse(active, closed, byType, byStatus, bySeverity,
                slaCard(scope, SlaStatus.BREACHED),
                slaCard(scope, SlaStatus.DUE_SOON),
                card(tickets, ticket -> "PROPOSAL".equals(ticket.getCurrentState().getKey()), newestFirst),
                card(tickets, ticket -> "CLIENT_CONFIRMATION".equals(ticket.getCurrentState().getKey()), newestFirst),
                principal.party() == Responsibility.CLIENT ? List.of()
                        : card(tickets, ticket -> ticket.getTicketLead() != null
                                && ticket.getTicketLead().getId().equals(principal.userId()), newestFirst));
    }

    /**
     * Builds a dashboard card for tickets matching a specific SLA status.
     */
    private List<TicketSummaryResponse> slaCard(Specification<Ticket> scope, SlaStatus status) {
        var page = ticketRepository.findAll(scope.and(
                SlaSpecifications.hasStatus(status, clock.instant(), slaCalculator)),
                PageRequest.of(0, CARD_LIMIT, Sort.by("updatedAt").descending()));
        return page.stream().map(this::summary).toList();
    }

    /**
     * Builds a sorted, limited dashboard card from an in-memory ticket list.
     */
    private List<TicketSummaryResponse> card(List<Ticket> tickets,
            java.util.function.Predicate<Ticket> predicate, Comparator<Ticket> order) {
        return tickets.stream().filter(predicate).sorted(order).limit(CARD_LIMIT).map(this::summary).toList();
    }

    private TicketSummaryResponse summary(Ticket ticket) {
        return TicketSummaryResponse.from(ticket, slaStatusService.status(ticket));
    }

    /**
     * Restricts dashboard data to the current organization for client users.
     */
    private Specification<Ticket> tenantScope(AuthPrincipal principal) {
        if (principal.party() == Responsibility.CLIENT) {
            return (root, query, cb) -> cb.equal(root.get("organization").get("id"), principal.organizationId());
        }
        return Specification.where(null);
    }

    private Map<String, Long> countBy(List<Ticket> tickets, Function<Ticket, String> classifier) {
        return tickets.stream().collect(Collectors.groupingBy(classifier, TreeMap::new, Collectors.counting()));
    }
}
