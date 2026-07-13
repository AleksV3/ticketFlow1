package com.ticketflow1.ticketing.workflow;

import com.ticketflow1.ticketing.common.Auditable;
import com.ticketflow1.ticketing.rbac.Permission;
import com.ticketflow1.ticketing.ticket.Responsibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "workflow_transition")
public class WorkflowTransition extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id")
    private Workflow workflow;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_state_id")
    private WorkflowState fromState;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_state_id")
    private WorkflowState toState;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "required_permission_id")
    private Permission requiredPermission;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_party", length = 12)
    private Responsibility requiredParty;

    @Enumerated(EnumType.STRING)
    @Column(name = "responsibility_after", length = 12)
    private Responsibility responsibilityAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_kind", nullable = false, length = 20)
    private TransitionOperationKind operationKind = TransitionOperationKind.STANDARD;

    protected WorkflowTransition() {
        // JPA
    }

    public WorkflowTransition(Workflow workflow, WorkflowState fromState, WorkflowState toState,
            Permission requiredPermission, Responsibility requiredParty,
            Responsibility responsibilityAfter) {
        this.workflow = workflow;
        this.fromState = fromState;
        this.toState = toState;
        this.requiredPermission = requiredPermission;
        this.requiredParty = requiredParty;
        this.responsibilityAfter = responsibilityAfter;
    }

    public WorkflowTransition(Workflow workflow, WorkflowState fromState, WorkflowState toState,
            Permission requiredPermission, Responsibility requiredParty,
            Responsibility responsibilityAfter, TransitionOperationKind operationKind) {
        this(workflow, fromState, toState, requiredPermission, requiredParty, responsibilityAfter);
        this.operationKind = operationKind;
    }

    public Long getId() {
        return id;
    }

    public Workflow getWorkflow() {
        return workflow;
    }

    public WorkflowState getFromState() {
        return fromState;
    }

    public WorkflowState getToState() {
        return toState;
    }

    public Permission getRequiredPermission() {
        return requiredPermission;
    }

    public Responsibility getRequiredParty() {
        return requiredParty;
    }

    public Responsibility getResponsibilityAfter() {
        return responsibilityAfter;
    }

    public TransitionOperationKind getOperationKind() { return operationKind; }
}
