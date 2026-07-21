package com.ticketflow1.ticketing.ticketconfig;
import com.ticketflow1.ticketing.ticket.Ticket; import com.ticketflow1.ticketing.user.AppUser;
import com.ticketflow1.ticketing.workflow.WorkflowState; import jakarta.persistence.*; import java.time.Instant; import org.hibernate.annotations.CreationTimestamp;
@Entity @Table(name="ticket_decision")
public class TicketDecision {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="ticket_id") private Ticket ticket;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private DecisionKind kind;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private DecisionValue decision;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="actor_id") private AppUser actor;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="from_state_id") private WorkflowState fromState;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="to_state_id") private WorkflowState toState;
    @Column(length=2000) private String reason; @Column(name="observed_ticket_version",nullable=false) private long observedTicketVersion;
    @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected TicketDecision() {}
    public TicketDecision(Ticket ticket,DecisionKind kind,DecisionValue decision,AppUser actor,WorkflowState fromState,WorkflowState toState,String reason,long version){
        this.ticket=ticket;this.kind=kind;this.decision=decision;this.actor=actor;this.fromState=fromState;this.toState=toState;this.reason=reason;this.observedTicketVersion=version;}
    public Long getId(){return id;} public Ticket getTicket(){return ticket;} public DecisionKind getKind(){return kind;} public DecisionValue getDecision(){return decision;}
    public AppUser getActor(){return actor;} public WorkflowState getFromState(){return fromState;} public WorkflowState getToState(){return toState;}
    public String getReason(){return reason;} public long getObservedTicketVersion(){return observedTicketVersion;} public Instant getCreatedAt(){return createdAt;}
}
