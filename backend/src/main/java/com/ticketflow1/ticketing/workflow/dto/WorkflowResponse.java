package com.ticketflow1.ticketing.workflow.dto;

import com.ticketflow1.ticketing.workflow.*;
import java.util.Comparator;
import java.util.List;

public record WorkflowResponse(Long id, String name, Long organizationId, long version,
        List<State> states, List<Transition> transitions) {
    public static WorkflowResponse from(Workflow workflow, List<WorkflowState> states,
            List<WorkflowTransition> transitions) {
        return new WorkflowResponse(workflow.getId(), workflow.getName(),
                workflow.getOrganization() == null ? null : workflow.getOrganization().getId(), workflow.getVersion(),
                states.stream().map(State::from).toList(),
                transitions.stream().sorted(Comparator.comparing(WorkflowTransition::getId)).map(Transition::from).toList());
    }
    public record State(Long id, String key, boolean isInitial, boolean isTerminal, int sortOrder) {
        static State from(WorkflowState s) { return new State(s.getId(), s.getKey(), s.isInitial(), s.isTerminal(), s.getSortOrder()); }
    }
    public record Transition(Long id, Long fromStateId, Long toStateId, String requiredPermission,
            String requiredParty, String responsibilityAfter, String operationKind) {
        static Transition from(WorkflowTransition t) { return new Transition(t.getId(), t.getFromState().getId(),
                t.getToState().getId(), t.getRequiredPermission().getKey(),
                t.getRequiredParty() == null ? null : t.getRequiredParty().name(),
                t.getResponsibilityAfter() == null ? null : t.getResponsibilityAfter().name(), t.getOperationKind().name()); }
    }
}
