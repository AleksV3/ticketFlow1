package com.ticketflow1.ticketing.proposal;

import com.ticketflow1.ticketing.common.Auditable;
import com.ticketflow1.ticketing.ticket.Ticket;
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
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "change_proposal")
public class ChangeProposal extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "estimated_delivery_date")
    private LocalDate estimatedDeliveryDate;

    @Column(name = "effort_estimate", length = 100)
    private String effortEstimate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProposalStatus status = ProposalStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id")
    private AppUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id")
    private AppUser decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ChangeProposal() {
        // JPA
    }

    public ChangeProposal(Ticket ticket, String description, LocalDate estimatedDeliveryDate,
            String effortEstimate, AppUser createdBy) {
        this.ticket = ticket;
        this.description = description;
        this.estimatedDeliveryDate = estimatedDeliveryDate;
        this.effortEstimate = effortEstimate;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public Ticket getTicket() { return ticket; }
    public String getDescription() { return description; }
    public LocalDate getEstimatedDeliveryDate() { return estimatedDeliveryDate; }
    public String getEffortEstimate() { return effortEstimate; }
    public ProposalStatus getStatus() { return status; }
    public AppUser getCreatedBy() { return createdBy; }
    public AppUser getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
