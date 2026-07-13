package com.ticketflow1.ticketing.dashboard;

import com.ticketflow1.ticketing.ticket.dto.TicketSummaryResponse;
import java.util.List;
import java.util.Map;

public record DashboardResponse(
        long activeCount,
        long closedCount,
        Map<String, Long> byType,
        Map<String, Long> byStatus,
        Map<String, Long> defectsBySeverity,
        List<TicketSummaryResponse> slaBreached,
        List<TicketSummaryResponse> slaDueSoon,
        List<TicketSummaryResponse> waitingForClientApproval,
        List<TicketSummaryResponse> waitingForClientConfirmation,
        List<TicketSummaryResponse> myAssignedTickets) {
}
