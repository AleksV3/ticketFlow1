package com.ticketflow1.ticketing.preference;

import java.util.List;

public enum DashboardWidget {
    MY_OPEN_TICKETS,
    MY_TEAM_TICKETS,
    TICKETS_BY_STATUS,
    TICKETS_BY_TYPE,
    AWAITING_MY_APPROVAL,
    RECENTLY_UPDATED;

    public static List<String> defaults() {
        return List.of(values()).stream().map(Enum::name).toList();
    }
}
