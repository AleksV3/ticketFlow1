package com.ticketflow1.ticketing.audit;

import com.ticketflow1.ticketing.audit.dto.AuditLogResponse;
import com.ticketflow1.ticketing.auth.AuthPrincipal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets/{ticketKey}/audit-log")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TICKET_READ')")
    public List<AuditLogResponse> list(@PathVariable String ticketKey,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return auditService.list(ticketKey, principal);
    }
}
