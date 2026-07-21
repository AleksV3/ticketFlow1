package com.ticketflow1.ticketing.sla;

import com.ticketflow1.ticketing.ticket.Severity;
import com.ticketflow1.ticketing.ticket.Ticket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SlaStatusService {

    private static final Duration MINIMUM_WARNING = Duration.ofMinutes(5);
    private final SlaCalculator calculator;
    private final Clock clock;

    public SlaStatusService(SlaCalculator calculator, Clock clock) {
        this.calculator = calculator;
        this.clock = clock;
    }

    public SlaStatus status(Ticket ticket) {
        return status(new SlaSnapshot(
                ticket.getTicketType().getCapability() == com.ticketflow1.ticketing.workflow.TicketTypeCapability.DEFECT_SLA,
                ticket.getCurrentState().isTerminal(),
                ticket.getSeverity(),
                ticket.getResponseDueAt(),
                ticket.getFirstInfoDueAt(),
                ticket.getNextUpdateDueAt(),
                ticket.getRespondedAt(),
                ticket.getFirstInfoAt()));
    }

    public SlaStatus status(SlaSnapshot snapshot) {
        if (!snapshot.defect() || snapshot.terminal() || snapshot.severity() == null) {
            return SlaStatus.NOT_APPLICABLE;
        }

        List<ActiveDeadline> active = activeDeadlines(snapshot);
        Instant now = clock.instant();
        if (active.stream().anyMatch(deadline -> !deadline.dueAt().isAfter(now))) {
            return SlaStatus.BREACHED;
        }
        if (active.stream().anyMatch(deadline -> isDueSoon(deadline, now))) {
            return SlaStatus.DUE_SOON;
        }
        return SlaStatus.OK;
    }

    private List<ActiveDeadline> activeDeadlines(SlaSnapshot snapshot) {
        List<ActiveDeadline> active = new ArrayList<>();
        if (snapshot.respondedAt() == null && snapshot.responseDueAt() != null) {
            active.add(new ActiveDeadline(snapshot.responseDueAt(), calculator.responseDuration(snapshot.severity())));
        }
        if (snapshot.firstInfoAt() == null && snapshot.firstInfoDueAt() != null) {
            active.add(new ActiveDeadline(snapshot.firstInfoDueAt(), calculator.firstInfoDuration(snapshot.severity())));
        }
        if (snapshot.nextUpdateDueAt() != null) {
            active.add(new ActiveDeadline(snapshot.nextUpdateDueAt(), calculator.nextUpdateDuration(snapshot.severity())));
        }
        return active;
    }

    private boolean isDueSoon(ActiveDeadline deadline, Instant now) {
        Duration quarter = deadline.originalDuration().dividedBy(4);
        Duration warning = quarter.compareTo(MINIMUM_WARNING) < 0 ? MINIMUM_WARNING : quarter;
        return !now.isBefore(deadline.dueAt().minus(warning));
    }

    private record ActiveDeadline(Instant dueAt, Duration originalDuration) {
    }

    public record SlaSnapshot(
            boolean defect,
            boolean terminal,
            Severity severity,
            Instant responseDueAt,
            Instant firstInfoDueAt,
            Instant nextUpdateDueAt,
            Instant respondedAt,
            Instant firstInfoAt) {
    }
}
