package com.ticketflow1.ticketing.workflow.dto;

import com.ticketflow1.ticketing.ticket.Responsibility;
import com.ticketflow1.ticketing.workflow.TransitionOperationKind;
import com.ticketflow1.ticketing.workflow.TicketTypeCapability;
import java.util.List;

public final class WorkflowRequests {
    private WorkflowRequests() {}
    public record State(String key, boolean isInitial, boolean isTerminal, int sortOrder) {}
    public record Transition(String fromState, String toState, String requiredPermission,
            Responsibility requiredParty, Responsibility responsibilityAfter, TransitionOperationKind operationKind) {}
    public record Create(String name, Long organizationId, List<State> states, List<Transition> transitions) {}
    public record Update(Long version, List<State> states, List<Transition> transitions, String canvasLayout) {}
    public record RenameState(Long version, String name) {}
    public record CreateType(String key, String name, Long workflowId, Long organizationId,
            boolean requiresProposal, Boolean active, Integer sortOrder, TicketTypeCapability capability) {}
    public record UpdateType(Long version, String name, Long workflowId, Boolean active, Integer sortOrder,
            TicketTypeCapability capability) {}
}
