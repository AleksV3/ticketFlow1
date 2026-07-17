package com.ticketflow1.ticketing.dashboard;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
/**
 * Dashboard API.
 *
 * It exposes the single aggregated snapshot used by the landing page.
 */
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Returns the current dashboard aggregates.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('TICKET_READ')")
    public DashboardResponse get(@AuthenticationPrincipal AuthPrincipal principal) {
        return dashboardService.get(principal);
    }
}
