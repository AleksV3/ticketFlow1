package com.ticketflow1.ticketing.team;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/teams") @PreAuthorize("hasAuthority('TICKET_READ')")
public class DeveloperTeamController {
    private final DeveloperTeamService service; public DeveloperTeamController(DeveloperTeamService service){this.service=service;}
    @GetMapping public List<TeamDtos.TeamResponse> list(@AuthenticationPrincipal AuthPrincipal p){return service.list(p);}
    @GetMapping("/options") public TeamDtos.Options options(@AuthenticationPrincipal AuthPrincipal p){return service.options(p);}
    @PostMapping public TeamDtos.TeamResponse create(@RequestBody TeamDtos.SaveTeamRequest r,@AuthenticationPrincipal AuthPrincipal p){return service.create(r,p);}
    @PutMapping("/{id}") public TeamDtos.TeamResponse update(@PathVariable Long id,@RequestBody TeamDtos.SaveTeamRequest r,@AuthenticationPrincipal AuthPrincipal p){return service.update(id,r,p);}
    @PutMapping("/{id}/ticket-order") public TeamDtos.TeamResponse reorder(@PathVariable Long id,@RequestBody TeamDtos.ReorderTicketsRequest r,@AuthenticationPrincipal AuthPrincipal p){return service.reorder(id,r,p);}
}
