package com.ticketflow1.ticketing.workflow;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.workflow.dto.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Admin API for workflow and ticket-type configuration.
 *
 * This controller is a thin HTTP layer: it authenticates the current user,
 * checks the required permission for each operation, and delegates the actual
 * workflow rules, validation, and persistence work to {@link WorkflowAdminService}.
 */
@RestController @RequestMapping("/api/admin")
public class WorkflowAdminController {
    private final WorkflowAdminService service;
    public WorkflowAdminController(WorkflowAdminService service){this.service=service;}
    /**
     * Lists workflows visible to the current user.
     *
     * Clients always see workflows in their own organization. Admin users can
     * optionally scope the list to a specific organization via
     * {@code organizationId}.
     */
    @GetMapping("/workflows") @PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
    List<WorkflowResponse> workflows(@AuthenticationPrincipal AuthPrincipal p,@RequestParam(required=false)Long organizationId){return service.listWorkflows(p,organizationId);}
    /**
     * Creates a workflow with its initial states and transitions.
     */
    @PostMapping("/workflows") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
    WorkflowResponse createWorkflow(@AuthenticationPrincipal AuthPrincipal p,@RequestBody WorkflowRequests.Create r){return service.createWorkflow(p,r);}
    /**
     * Updates workflow metadata, states, and editable transitions.
     */
    @PatchMapping("/workflows/{id}") @PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
    WorkflowResponse updateWorkflow(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long id,@RequestBody WorkflowRequests.Update r){return service.updateWorkflow(p,id,r);}
    /**
     * Removes a workflow state after the service verifies it is safe to delete.
     */
    @DeleteMapping("/workflows/{id}/states/{stateId}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
    void removeState(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long id,@PathVariable Long stateId){service.removeState(p,id,stateId);}
    /**
     * Lists ticket types visible to the current user.
     */
    @GetMapping("/ticket-types") @PreAuthorize("hasAuthority('TYPE_MANAGE')")
    List<TicketTypeAdminResponse> types(@AuthenticationPrincipal AuthPrincipal p,@RequestParam(required=false)Long organizationId){return service.listTypes(p,organizationId);}
    /**
     * Creates a ticket type and associates it with a workflow.
     */
    @PostMapping("/ticket-types") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('TYPE_MANAGE')")
    TicketTypeAdminResponse createType(@AuthenticationPrincipal AuthPrincipal p,@RequestBody WorkflowRequests.CreateType r){return service.createType(p,r);}
    /**
     * Reassigns an existing ticket type to a different workflow.
     */
    @PatchMapping("/ticket-types/{id}") @PreAuthorize("hasAuthority('TYPE_MANAGE')")
    TicketTypeAdminResponse updateType(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long id,@RequestBody WorkflowRequests.UpdateType r){return service.updateType(p,id,r);}
    @PostMapping("/ticket-types/{id}/activate") @PreAuthorize("hasAuthority('TYPE_MANAGE')")
    TicketTypeAdminResponse activateType(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long id){return service.setTypeActive(p,id,true);}
    @PostMapping("/ticket-types/{id}/deactivate") @PreAuthorize("hasAuthority('TYPE_MANAGE')")
    TicketTypeAdminResponse deactivateType(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long id){return service.setTypeActive(p,id,false);}
    @DeleteMapping("/ticket-types/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('TYPE_MANAGE')")
    void deleteType(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long id){service.deleteType(p,id);}
}
