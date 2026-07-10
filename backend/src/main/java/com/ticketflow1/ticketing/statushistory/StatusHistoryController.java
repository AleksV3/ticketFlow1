package com.ticketflow1.ticketing.statushistory;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.statushistory.dto.StatusHistoryResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets/{ticketKey}/status-history")
public class StatusHistoryController {

    private final StatusHistoryService statusHistoryService;

    public StatusHistoryController(StatusHistoryService statusHistoryService) {
        this.statusHistoryService = statusHistoryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TICKET_READ')")
    public List<StatusHistoryResponse> list(@PathVariable String ticketKey,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return statusHistoryService.list(ticketKey, principal);
    }
}
