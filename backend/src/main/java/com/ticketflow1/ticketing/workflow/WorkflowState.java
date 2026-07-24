package com.ticketflow1.ticketing.workflow;

import com.ticketflow1.ticketing.common.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "workflow_state")
public class WorkflowState extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id")
    private Workflow workflow;

    @Column(nullable = false, length = 40)
    private String key;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "is_initial", nullable = false)
    private boolean initial;

    @Column(name = "is_terminal", nullable = false)
    private boolean terminal;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected WorkflowState() {
        // JPA
    }

    public WorkflowState(Workflow workflow, String key, boolean initial, boolean terminal, int sortOrder) {
        this.workflow = workflow;
        this.key = key;
        this.name = key;
        this.initial = initial;
        this.terminal = terminal;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public Workflow getWorkflow() {
        return workflow;
    }

    public String getKey() {
        return key;
    }

    public String getName() { return name; }

    /** Renames this state in place; transition rows reference the state id and
     * therefore remain connected when the display name changes. */
    public void rename(String name) {
        this.name = name;
    }

    public boolean isInitial() {
        return initial;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    /** Reordering changes presentation only; state identity and workflow rules stay intact. */
    public void reorder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
