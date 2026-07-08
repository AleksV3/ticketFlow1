package com.ticketflow1.ticketing.user;

import com.ticketflow1.ticketing.common.Auditable;
import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.rbac.Role;
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
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "app_user")
public class AppUser extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    // Structural, never derived from role — a role's party must match this.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Responsibility party;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id")
    private Role role;

    // Null for TicketFlow1-side users; required for CLIENT (enforced in UserService).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {
        // JPA
    }

    public AppUser(String email, String passwordHash, String displayName, Responsibility party,
            Role role, Organization organization) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.party = party;
        this.role = role;
        this.organization = organization;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Responsibility getParty() {
        return party;
    }

    public Role getRole() {
        return role;
    }

    public Organization getOrganization() {
        return organization;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
