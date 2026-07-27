package com.ticketflow1.ticketing.notification;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/tickets/{ticketKey}/follow") @PreAuthorize("hasAuthority('TICKET_READ')")
public class TicketFollowController {
    private final NotificationService service;
    public TicketFollowController(NotificationService service){this.service=service;}
    @GetMapping public boolean following(@PathVariable String ticketKey,@AuthenticationPrincipal AuthPrincipal principal){return service.isFollowing(ticketKey,principal);}
    @PutMapping @ResponseStatus(HttpStatus.NO_CONTENT) public void follow(@PathVariable String ticketKey,@AuthenticationPrincipal AuthPrincipal principal){service.follow(ticketKey,principal);}
    @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT) public void unfollow(@PathVariable String ticketKey,@AuthenticationPrincipal AuthPrincipal principal){service.unfollow(ticketKey,principal);}
}
