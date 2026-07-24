package com.ticketflow1.ticketing.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.sla.SlaCalculator;
import com.ticketflow1.ticketing.sla.SlaStatusService;
import com.ticketflow1.ticketing.ticket.Responsibility;
import com.ticketflow1.ticketing.ticket.TicketRepository;
import com.ticketflow1.ticketing.ticketconfig.TicketApprovalRepository;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketApprovalRepository ticketApprovalRepository;
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        SlaCalculator calculator = new SlaCalculator();
        Clock clock = Clock.systemUTC();
        dashboardService = new DashboardService(ticketRepository, calculator,
                new SlaStatusService(calculator, clock), clock, ticketApprovalRepository);
    }

    @Test
    void emptyTenantHasUniformEmptyDashboard() {
        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(1)));
        AuthPrincipal principal = new AuthPrincipal(10L, Responsibility.CLIENT, 99L, Set.of("TICKET_READ"));

        DashboardResponse response = dashboardService.get(principal);

        assertThat(response.activeCount()).isZero();
        assertThat(response.closedCount()).isZero();
        assertThat(response.byType()).isEmpty();
        assertThat(response.byStatus()).isEmpty();
        assertThat(response.defectsBySeverity()).isEmpty();
        assertThat(response.slaBreached()).isEmpty();
        assertThat(response.slaDueSoon()).isEmpty();
        assertThat(response.waitingForClientApproval()).isEmpty();
        assertThat(response.waitingForClientConfirmation()).isEmpty();
        assertThat(response.myAssignedTickets()).isEmpty();
        assertThat(response.myOpenTickets()).isEmpty();
        assertThat(response.myTeamTickets()).isEmpty();
        assertThat(response.awaitingMyApproval()).isEmpty();
        assertThat(response.recentlyUpdated()).isEmpty();
    }
}
