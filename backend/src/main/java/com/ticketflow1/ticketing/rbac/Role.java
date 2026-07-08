package com.ticketflow1.ticketing.rbac;

import com.ticketflow1.ticketing.common.Auditable;
import com.ticketflow1.ticketing.organization.Organization;
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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

/**
 * A configurable bundle of permissions (FR-009) — the golden rule is that
 * code checks permissions, never role names. TicketFlow1-party roles are
 * global (organization null); CLIENT-party roles are cloned per Organization
 * from the {@code isTemplate} rows on org creation (FR-022,
 * {@code clone_org_templates()} in V2) and never assigned to a user directly.
 */
@Entity
@Table(name = "role")
public class Role extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Responsibility party;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(name = "is_template", nullable = false)
    private boolean template;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {
        // JPA
    }

    public Role(String name, Responsibility party, Organization organization, boolean template) {
        this.name = name;
        this.party = party;
        this.organization = organization;
        this.template = template;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Responsibility getParty() {
        return party;
    }

    public Organization getOrganization() {
        return organization;
    }

    public boolean isTemplate() {
        return template;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public boolean hasPermission(String permissionKey) {
        return permissions.stream().anyMatch(p -> p.getKey().equals(permissionKey));
    }
}
