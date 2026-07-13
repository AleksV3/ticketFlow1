package com.ticketflow1.ticketing.sla;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketflow1.ticketing.sla.SlaStatusService.SlaSnapshot;
import com.ticketflow1.ticketing.ticket.Severity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SlaStatusServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T10:00:00Z");
    private final SlaStatusService service = new SlaStatusService(
            new SlaCalculator(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void appliesNotApplicableAndBreachPrecedence() {
        assertThat(service.status(snapshot(false, false, Severity.SEV_1, NOW.plusSeconds(60), null)))
                .isEqualTo(SlaStatus.NOT_APPLICABLE);
        assertThat(service.status(snapshot(true, true, Severity.SEV_1, NOW.minusSeconds(60), null)))
                .isEqualTo(SlaStatus.NOT_APPLICABLE);
        assertThat(service.status(snapshot(true, false, Severity.SEV_1, NOW.minusSeconds(1), null)))
                .isEqualTo(SlaStatus.BREACHED);
    }

    @Test
    void warningWindowUsesFinalQuarterWithFiveMinuteMinimum() {
        assertThat(service.status(snapshot(true, false, Severity.SEV_1, NOW.plusSeconds(4 * 60), null)))
                .isEqualTo(SlaStatus.DUE_SOON);
        assertThat(service.status(snapshot(true, false, Severity.SEV_1, NOW.plusSeconds(6 * 60), null)))
                .isEqualTo(SlaStatus.OK);
        assertThat(service.status(snapshot(true, false, Severity.SEV_2, null, NOW.plusSeconds(14 * 60))))
                .isEqualTo(SlaStatus.DUE_SOON);
    }

    @Test
    void completedMilestonesNoLongerContribute() {
        SlaSnapshot completed = new SlaSnapshot(true, false, Severity.SEV_3,
                NOW.minusSeconds(3600), NOW.minusSeconds(1800), null, NOW.minusSeconds(3500), NOW.minusSeconds(1700));
        assertThat(service.status(completed)).isEqualTo(SlaStatus.OK);
    }

    private SlaSnapshot snapshot(boolean defect, boolean terminal, Severity severity,
            Instant responseDue, Instant firstInfoDue) {
        return new SlaSnapshot(defect, terminal, severity, responseDue, firstInfoDue,
                null, null, null);
    }
}
