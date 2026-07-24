package com.ticketflow1.ticketing.preference;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.preference.dto.PreferenceResponse;
import com.ticketflow1.ticketing.preference.dto.ReplacePreferenceRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/preferences")
@PreAuthorize("hasAuthority('TICKET_READ')")
public class PreferenceController {

    private final PreferenceService preferenceService;

    public PreferenceController(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public PreferenceResponse get(@AuthenticationPrincipal AuthPrincipal principal) {
        return preferenceService.get(principal);
    }

    @PutMapping
    public PreferenceResponse replace(@Valid @RequestBody ReplacePreferenceRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return preferenceService.replace(request, principal);
    }

    @DeleteMapping
    public PreferenceResponse reset(@AuthenticationPrincipal AuthPrincipal principal) {
        return preferenceService.reset(principal);
    }
}
