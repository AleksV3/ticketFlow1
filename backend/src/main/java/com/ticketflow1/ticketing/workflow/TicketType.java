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
import jakarta.persistence.Table;

@Entity
@Table(name = "ticket_type")
public class TicketType extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String key;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id")
    private Workflow workflow;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(name = "is_template", nullable = false)
    private boolean template;

    @Column(name = "requires_proposal", nullable = false)
    private boolean requiresProposal;

    protected TicketType() {
        // JPA
    }

    public TicketType(String key, String name, Workflow workflow, Organization organization,
            boolean template, boolean requiresProposal) {
        this.key = key;
        this.name = name;
        this.workflow = workflow;
        this.organization = organization;
        this.template = template;
        this.requiresProposal = requiresProposal;
    }

    public Long getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public Workflow getWorkflow() {
        return workflow;
    }

    public Organization getOrganization() {
        return organization;
    }

    public boolean isTemplate() {
        return template;
    }

    public boolean isRequiresProposal() {
        return requiresProposal;
    }

    public void applyWorkflow(Workflow workflow) {
        this.workflow = workflow;
    }
}
