package com.ticketflow1.ticketing.workflow;

import com.ticketflow1.ticketing.common.Auditable;
import com.ticketflow1.ticketing.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "workflow")
public class Workflow extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @OneToMany(mappedBy = "workflow")
    @OrderBy("sortOrder ASC")
    private Set<WorkflowState> states = new LinkedHashSet<>();

    @OneToMany(mappedBy = "workflow")
    private Set<WorkflowTransition> transitions = new LinkedHashSet<>();

    protected Workflow() {
        // JPA
    }

    public Workflow(String name, Organization organization) {
        this.name = name;
        this.organization = organization;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Set<WorkflowState> getStates() {
        return states;
    }

    public Set<WorkflowTransition> getTransitions() {
        return transitions;
    }
}
