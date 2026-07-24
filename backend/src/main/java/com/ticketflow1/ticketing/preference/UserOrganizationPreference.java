package com.ticketflow1.ticketing.preference;

import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.team.DeveloperTeam;
import com.ticketflow1.ticketing.user.AppUser;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_organization_preference")
public class UserOrganizationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dashboard_widgets", nullable = false, columnDefinition = "jsonb")
    private List<String> dashboardWidgets = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "enabled_ticket_filters", nullable = false, columnDefinition = "jsonb")
    private List<String> enabledTicketFilters = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_viewed_team_id")
    private DeveloperTeam lastViewedTeam;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private PreferenceTheme theme = PreferenceTheme.SYSTEM;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserOrganizationPreference() {
    }

    public UserOrganizationPreference(AppUser user, Organization organization) {
        this.user = user;
        this.organization = organization;
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public Organization getOrganization() { return organization; }
    public List<String> getDashboardWidgets() { return dashboardWidgets; }
    public List<String> getEnabledTicketFilters() { return enabledTicketFilters; }
    public DeveloperTeam getLastViewedTeam() { return lastViewedTeam; }
    public PreferenceTheme getTheme() { return theme; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void replace(List<String> widgets, List<String> filters,
            DeveloperTeam team, PreferenceTheme nextTheme) {
        dashboardWidgets = new ArrayList<>(widgets);
        enabledTicketFilters = new ArrayList<>(filters);
        lastViewedTeam = team;
        theme = nextTheme;
    }
}
