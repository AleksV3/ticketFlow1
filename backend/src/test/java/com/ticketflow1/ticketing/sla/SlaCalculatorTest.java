package com.ticketflow1.ticketing.sla;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketflow1.ticketing.ticket.Severity;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SlaCalculatorTest {

    private final SlaCalculator calculator = new SlaCalculator();

    @Test
    void calculatesEverySeverityFormula() {
        Instant created = Instant.parse("2026-07-13T08:00:00Z"); // Monday 10:00 Ljubljana

        assertThat(calculator.calculate(Severity.SEV_1, created, created))
                .isEqualTo(new SlaCalculator.SlaDeadlines(
                        created.plusSeconds(15 * 60), created.plusSeconds(45 * 60), created.plusSeconds(120 * 60)));
        assertThat(calculator.calculate(Severity.SEV_2, created, created))
                .isEqualTo(new SlaCalculator.SlaDeadlines(
                        created.plusSeconds(30 * 60), created.plusSeconds(60 * 60), created.plusSeconds(240 * 60)));
        assertThat(calculator.calculate(Severity.SEV_3, created, created))
                .isEqualTo(new SlaCalculator.SlaDeadlines(
                        created.plusSeconds(60 * 60), created.plusSeconds(90 * 60), null));
        assertThat(calculator.calculate(Severity.SEV_4, created, created).responseDueAt())
                .isEqualTo(Instant.parse("2026-07-14T06:00:00Z"));
    }

    @Test
    void sev3OutsideBusinessHoursAndWeekendStartNextWeekdayAtEight() {
        var fridayEvening = calculator.calculate(
                Severity.SEV_3, Instant.parse("2026-07-10T18:00:00Z"), null);
        assertThat(fridayEvening.responseDueAt()).isEqualTo(Instant.parse("2026-07-13T07:00:00Z"));
        assertThat(fridayEvening.firstInfoDueAt()).isEqualTo(Instant.parse("2026-07-13T07:30:00Z"));

        var sunday = calculator.calculate(Severity.SEV_4, Instant.parse("2026-07-12T10:00:00Z"), null);
        assertThat(sunday.responseDueAt()).isEqualTo(Instant.parse("2026-07-13T06:00:00Z"));
    }
}
