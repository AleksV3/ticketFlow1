package com.ticketflow1.ticketing.preference.dto;

import java.util.List;

public record PreferenceResponse(
        List<String> dashboardWidgets,
        List<String> enabledTicketFilters,
        Long lastViewedTeamId,
        String theme,
        long version) {
}
