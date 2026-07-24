package com.ticketflow1.ticketing.ticketconfig;

import com.ticketflow1.ticketing.team.DeveloperTeam;
import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.user.AppUser;
import com.ticketflow1.ticketing.workflow.WorkflowState;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "ticket_approval")
public class TicketApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pending_state_id")
    private WorkflowState pendingState;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_approver_id")
    private AppUser assignedApprover;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_team_id")
    private DeveloperTeam assignedTeam;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TicketApprovalStatus status = TicketApprovalStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id")
    private AppUser decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TicketApproval() {
    }

    public TicketApproval(Ticket ticket, WorkflowState pendingState,
            AppUser assignedApprover, DeveloperTeam assignedTeam) {
        if (assignedApprover == null && assignedTeam == null) {
            throw new IllegalArgumentException("An approval requires an approver or assigned team.");
        }
        this.ticket = ticket;
        this.pendingState = pendingState;
        this.assignedApprover = assignedApprover;
        this.assignedTeam = assignedTeam;
    }

    public Long getId() { return id; }
    public Ticket getTicket() { return ticket; }
    public WorkflowState getPendingState() { return pendingState; }
    public AppUser getAssignedApprover() { return assignedApprover; }
    public DeveloperTeam getAssignedTeam() { return assignedTeam; }
    public TicketApprovalStatus getStatus() { return status; }
    public AppUser getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void decide(TicketApprovalStatus decision, AppUser actor, Instant at) {
        if (status != TicketApprovalStatus.PENDING) {
            throw new IllegalStateException("Approval has already been decided.");
        }
        if (decision == TicketApprovalStatus.PENDING) {
            throw new IllegalArgumentException("Decision cannot remain pending.");
        }
        status = decision;
        decidedBy = actor;
        decidedAt = at;
    }
}

