package com.ticketflow1.ticketing.workflow;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.workflow.dto.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin")
public class WorkflowAdminController {
    private final WorkflowAdminService service;
    public WorkflowAdminController(WorkflowAdminService service){this.service=service;}
    @GetMapping("/workflows") @PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
    List<WorkflowResponse> workflows(@AuthenticationPrincipal AuthPrincipal p,@RequestParam(required=false)Long organizationId){return service.listWorkflows(p,organizationId);}
    @PostMapping("/workflows") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
    WorkflowResponse createWorkflow(@AuthenticationPrincipal AuthPrincipal p,@RequestBody WorkflowRequests.Create r){return service.createWorkflow(p,r);}
    @PatchMapping("/workflows/{id}") @PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
    WorkflowResponse updateWorkflow(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long id,@RequestBody WorkflowRequests.Update r){return service.updateWorkflow(p,id,r);}
    @GetMapping("/ticket-types") @PreAuthorize("hasAuthority('TYPE_MANAGE')")
    List<TicketTypeAdminResponse> types(@AuthenticationPrincipal AuthPrincipal p,@RequestParam(required=false)Long organizationId){return service.listTypes(p,organizationId);}
    @PostMapping("/ticket-types") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('TYPE_MANAGE')")
    TicketTypeAdminResponse createType(@AuthenticationPrincipal AuthPrincipal p,@RequestBody WorkflowRequests.CreateType r){return service.createType(p,r);}
}
