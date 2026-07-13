package com.ticketflow1.ticketing.sla;

import com.ticketflow1.ticketing.ticket.Severity;
import com.ticketflow1.ticketing.ticket.Ticket;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class SlaSpecifications {

    private static final Duration MINIMUM_WARNING = Duration.ofMinutes(5);

    private SlaSpecifications() {
    }

    public static Specification<Ticket> hasStatus(SlaStatus status, Instant now, SlaCalculator calculator) {
        return (root, query, cb) -> {
            Predicate applicable = applicable(root, cb);
            Predicate breached = breached(root, cb, now);
            Predicate dueSoon = dueSoon(root, cb, now, calculator);
            return switch (status) {
                case NOT_APPLICABLE -> cb.not(applicable);
                case BREACHED -> cb.and(applicable, breached);
                case DUE_SOON -> cb.and(applicable, cb.not(breached), dueSoon);
                case OK -> cb.and(applicable, cb.not(breached), cb.not(dueSoon));
            };
        };
    }

    private static Predicate applicable(Root<Ticket> root, CriteriaBuilder cb) {
        return cb.and(
                cb.equal(root.get("ticketType").get("key"), "DEFECT"),
                cb.isFalse(root.get("currentState").get("terminal")),
                cb.isNotNull(root.get("severity")));
    }

    private static Predicate breached(Root<Ticket> root, CriteriaBuilder cb, Instant now) {
        return cb.or(
                activeBy(root, cb, "responseDueAt", "respondedAt", now, null),
                activeBy(root, cb, "firstInfoDueAt", "firstInfoAt", now, null),
                activeBy(root, cb, "nextUpdateDueAt", null, now, null));
    }

    private static Predicate dueSoon(Root<Ticket> root, CriteriaBuilder cb, Instant now,
            SlaCalculator calculator) {
        List<Predicate> windows = new ArrayList<>();
        for (Severity severity : Severity.values()) {
            Predicate matchesSeverity = cb.equal(root.get("severity"), severity);
            windows.add(cb.and(matchesSeverity,
                    activeBy(root, cb, "responseDueAt", "respondedAt", now,
                            warning(calculator.responseDuration(severity)))));
            Duration firstInfo = calculator.firstInfoDuration(severity);
            if (firstInfo != null) {
                windows.add(cb.and(matchesSeverity,
                        activeBy(root, cb, "firstInfoDueAt", "firstInfoAt", now, warning(firstInfo))));
            }
            Duration nextUpdate = calculator.nextUpdateDuration(severity);
            if (nextUpdate != null) {
                windows.add(cb.and(matchesSeverity,
                        activeBy(root, cb, "nextUpdateDueAt", null, now, warning(nextUpdate))));
            }
        }
        return cb.or(windows.toArray(Predicate[]::new));
    }

    private static Predicate activeBy(Root<Ticket> root, CriteriaBuilder cb, String dueField,
            String completionField, Instant now, Duration warning) {
        Path<Instant> dueAt = root.get(dueField);
        Predicate unfinished = completionField == null ? cb.conjunction() : cb.isNull(root.get(completionField));
        if (warning == null) {
            return cb.and(unfinished, cb.isNotNull(dueAt), cb.lessThanOrEqualTo(dueAt, now));
        }
        return cb.and(unfinished, cb.isNotNull(dueAt), cb.greaterThan(dueAt, now),
                cb.lessThanOrEqualTo(dueAt, now.plus(warning)));
    }

    private static Duration warning(Duration duration) {
        Duration quarter = duration.dividedBy(4);
        return quarter.compareTo(MINIMUM_WARNING) < 0 ? MINIMUM_WARNING : quarter;
    }
}
