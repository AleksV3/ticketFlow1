package com.ticketflow1.ticketing.notification;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service){this.service=service;}
    @GetMapping public List<NotificationService.NotificationResponse> list(@AuthenticationPrincipal AuthPrincipal principal){return service.list(principal.userId());}
    @GetMapping("/unread-count") public long unreadCount(@AuthenticationPrincipal AuthPrincipal principal){return service.unreadCount(principal.userId());}
    @PatchMapping("/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT) public void markRead(@PathVariable Long id,@AuthenticationPrincipal AuthPrincipal principal){service.markRead(id,principal.userId());}
    @PostMapping("/read-all") @ResponseStatus(HttpStatus.NO_CONTENT) public void markAllRead(@AuthenticationPrincipal AuthPrincipal principal){service.markAllRead(principal.userId());}
}
