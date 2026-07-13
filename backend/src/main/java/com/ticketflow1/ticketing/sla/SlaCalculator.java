package com.ticketflow1.ticketing.sla;

import com.ticketflow1.ticketing.ticket.Severity;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

@Component
public class SlaCalculator {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Ljubljana");
    private static final LocalTime BUSINESS_START = LocalTime.of(8, 0);
    private static final LocalTime BUSINESS_END = LocalTime.of(17, 0);

    public SlaDeadlines calculate(Severity severity, Instant createdAt, Instant lastUpdateAt) {
        if (severity == null || createdAt == null) {
            throw new IllegalArgumentException("Severity and creation time are required for SLA calculation.");
        }
        Instant updateBase = lastUpdateAt == null ? createdAt : lastUpdateAt;
        return switch (severity) {
            case SEV_1 -> new SlaDeadlines(
                    createdAt.plus(Duration.ofMinutes(15)),
                    createdAt.plus(Duration.ofMinutes(45)),
                    updateBase.plus(Duration.ofMinutes(120)));
            case SEV_2 -> new SlaDeadlines(
                    createdAt.plus(Duration.ofMinutes(30)),
                    createdAt.plus(Duration.ofMinutes(60)),
                    updateBase.plus(Duration.ofMinutes(240)));
            case SEV_3 -> {
                Instant businessBase = businessTimeBase(createdAt);
                yield new SlaDeadlines(
                        businessBase.plus(Duration.ofMinutes(60)),
                        businessBase.plus(Duration.ofMinutes(90)),
                        null);
            }
            case SEV_4 -> new SlaDeadlines(nextBusinessDay(createdAt), null, null);
        };
    }

    public Duration responseDuration(Severity severity) {
        return switch (severity) {
            case SEV_1 -> Duration.ofMinutes(15);
            case SEV_2 -> Duration.ofMinutes(30);
            case SEV_3 -> Duration.ofMinutes(60);
            case SEV_4 -> Duration.ofHours(24);
        };
    }

    public Duration firstInfoDuration(Severity severity) {
        return switch (severity) {
            case SEV_1 -> Duration.ofMinutes(45);
            case SEV_2 -> Duration.ofMinutes(60);
            case SEV_3 -> Duration.ofMinutes(90);
            case SEV_4 -> null;
        };
    }

    public Duration nextUpdateDuration(Severity severity) {
        return switch (severity) {
            case SEV_1 -> Duration.ofMinutes(120);
            case SEV_2 -> Duration.ofMinutes(240);
            case SEV_3, SEV_4 -> null;
        };
    }

    private Instant businessTimeBase(Instant instant) {
        ZonedDateTime local = instant.atZone(BUSINESS_ZONE);
        if (isWeekday(local.getDayOfWeek())
                && !local.toLocalTime().isBefore(BUSINESS_START)
                && local.toLocalTime().isBefore(BUSINESS_END)) {
            return instant;
        }
        return nextBusinessStart(local).toInstant();
    }

    private Instant nextBusinessDay(Instant instant) {
        return nextBusinessStart(instant.atZone(BUSINESS_ZONE)).toInstant();
    }

    private ZonedDateTime nextBusinessStart(ZonedDateTime local) {
        ZonedDateTime candidate = local.plusDays(1).with(BUSINESS_START);
        while (!isWeekday(candidate.getDayOfWeek())) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    private boolean isWeekday(DayOfWeek day) {
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    public record SlaDeadlines(
            Instant responseDueAt,
            Instant firstInfoDueAt,
            Instant nextUpdateDueAt) {
    }
}
