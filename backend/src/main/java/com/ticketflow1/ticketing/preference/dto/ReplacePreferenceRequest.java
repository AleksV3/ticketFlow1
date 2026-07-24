package com.ticketflow1.ticketing.preference.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReplacePreferenceRequest(
        @NotNull @Size(max = 6) List<String> dashboardWidgets,
        @NotNull @Size(max = 12) List<String> enabledTicketFilters,
        Long lastViewedTeamId,
        @NotNull String theme,
        @NotNull Long version) {
}
