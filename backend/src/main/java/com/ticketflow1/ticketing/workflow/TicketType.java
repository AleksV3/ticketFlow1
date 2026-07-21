package com.ticketflow1.ticketing.workflow;

import com.ticketflow1.ticketing.common.Auditable;
import com.ticketflow1.ticketing.organization.Organization;
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
import jakarta.persistence.Version;

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

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketTypeCapability capability = TicketTypeCapability.STANDARD;

    @Version
    @Column(nullable = false)
    private long version;

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

    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }
    public TicketTypeCapability getCapability() { return capability; }
    public long getVersion() { return version; }

    public void applyWorkflow(Workflow workflow) {
        this.workflow = workflow;
    }

    public void configure(String name, Workflow workflow, boolean active, int sortOrder, TicketTypeCapability capability) {
        this.name = name; this.workflow = workflow; this.active = active; this.sortOrder = sortOrder; this.capability = capability;
    }

    public void setActive(boolean active) { this.active = active; }
}
