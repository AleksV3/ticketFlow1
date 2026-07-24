package com.ticketflow1.ticketing.ticket;

import com.ticketflow1.ticketing.common.Auditable;
import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.team.DeveloperTeam;
import com.ticketflow1.ticketing.user.AppUser;
import com.ticketflow1.ticketing.workflow.TicketType;
import com.ticketflow1.ticketing.workflow.WorkflowState;
import com.ticketflow1.ticketing.ticketconfig.TicketSubtype;
import com.ticketflow1.ticketing.ticketconfig.SubtypeRoutingRule;
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
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "ticket")
public class Ticket extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_key", nullable = false, unique = true, length = 20)
    private String ticketKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_type_id")
    private TicketType ticketType;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "subtype_id") private TicketSubtype subtype;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_ticket_id") private Ticket parentTicket;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "routing_rule_id") private SubtypeRoutingRule routingRule;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "resolved_approver_id") private AppUser resolvedApprover;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "target_user_id") private AppUser targetUser;
    @Column(name = "target_user_display_snapshot", length = 255) private String targetUserDisplaySnapshot;

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "current_state_id")
    private WorkflowState currentState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(length = 6)
    private Severity severity;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_owner_id")
    private AppUser businessOwner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_lead_id")
    private AppUser ticketLead;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "ticket_developer",
            joinColumns = @JoinColumn(name = "ticket_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<AppUser> developers = new LinkedHashSet<>();

    @ManyToMany(mappedBy = "tickets", fetch = FetchType.LAZY)
    private Set<DeveloperTeam> teams = new LinkedHashSet<>();

    @Column(name = "assigned_team", length = 100)
    private String assignedTeam;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_responsibility", nullable = false, length = 12)
    private Responsibility currentResponsibility = Responsibility.TICKETFLOW1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "response_due_at")
    private Instant responseDueAt;

    @Column(name = "first_info_due_at")
    private Instant firstInfoDueAt;

    @Column(name = "next_update_due_at")
    private Instant nextUpdateDueAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "first_info_at")
    private Instant firstInfoAt;

    protected Ticket() {
        // JPA
    }

    public Ticket(String ticketKey, TicketType ticketType, WorkflowState currentState, Priority priority,
            Severity severity, String title, String description, Organization organization,
            AppUser businessOwner, Responsibility currentResponsibility) {
        this.ticketKey = ticketKey;
        this.ticketType = ticketType;
        this.currentState = currentState;
        this.priority = priority;
        this.severity = severity;
        this.title = title;
        this.description = description;
        this.organization = organization;
        this.businessOwner = businessOwner;
        this.currentResponsibility = currentResponsibility;
    }

    public Long getId() {
        return id;
    }

    public String getTicketKey() {
        return ticketKey;
    }

    public void setTicketKey(String ticketKey) {
        this.ticketKey = ticketKey;
    }

    public TicketType getTicketType() {
        return ticketType;
    }

    public WorkflowState getCurrentState() {
        return currentState;
    }
    public TicketSubtype getSubtype(){return subtype;} public void setSubtype(TicketSubtype value){subtype=value;}
    public Ticket getParentTicket(){return parentTicket;} public void setParentTicket(Ticket value){parentTicket=value;}
    public SubtypeRoutingRule getRoutingRule(){return routingRule;} public void setRoutingRule(SubtypeRoutingRule value){routingRule=value;}
    public AppUser getResolvedApprover(){return resolvedApprover;} public void setResolvedApprover(AppUser value){resolvedApprover=value;}
    public AppUser getTargetUser(){return targetUser;} public void setTargetUser(AppUser value){targetUser=value;}
    public String getTargetUserDisplaySnapshot(){return targetUserDisplaySnapshot;} public void setTargetUserDisplaySnapshot(String value){targetUserDisplaySnapshot=value;}
    public long getVersion(){return version;}

    public void setCurrentState(WorkflowState currentState) {
        this.currentState = currentState;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Organization getOrganization() {
        return organization;
    }

    public AppUser getBusinessOwner() {
        return businessOwner;
    }

    public AppUser getTicketLead() {
        return ticketLead;
    }

    public void setTicketLead(AppUser ticketLead) {
        this.ticketLead = ticketLead;
    }

    public Set<AppUser> getDevelopers() { return developers; }
    public void replaceDevelopers(Set<AppUser> developers) {
        this.developers.clear();
        this.developers.addAll(developers);
    }
    public Set<DeveloperTeam> getTeams() { return teams; }
    public void replaceTeams(Set<DeveloperTeam> teams) { this.teams.clear(); this.teams.addAll(teams); }

    public String getAssignedTeam() {
        return assignedTeam;
    }

    public void setAssignedTeam(String assignedTeam) {
        this.assignedTeam = assignedTeam;
    }

    public Responsibility getCurrentResponsibility() {
        return currentResponsibility;
    }

    public void setCurrentResponsibility(Responsibility currentResponsibility) {
        this.currentResponsibility = currentResponsibility;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public Instant getResponseDueAt() {
        return responseDueAt;
    }

    public void setResponseDueAt(Instant responseDueAt) {
        this.responseDueAt = responseDueAt;
    }

    public Instant getFirstInfoDueAt() {
        return firstInfoDueAt;
    }

    public void setFirstInfoDueAt(Instant firstInfoDueAt) {
        this.firstInfoDueAt = firstInfoDueAt;
    }

    public Instant getNextUpdateDueAt() {
        return nextUpdateDueAt;
    }

    public void setNextUpdateDueAt(Instant nextUpdateDueAt) {
        this.nextUpdateDueAt = nextUpdateDueAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }

    public Instant getFirstInfoAt() {
        return firstInfoAt;
    }

    public void setFirstInfoAt(Instant firstInfoAt) {
        this.firstInfoAt = firstInfoAt;
    }
}
