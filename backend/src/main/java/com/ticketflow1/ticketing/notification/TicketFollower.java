package com.ticketflow1.ticketing.notification;

import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.user.AppUser;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="ticket_follower")
public class TicketFollower {
    @EmbeddedId private Id id;
    @ManyToOne(fetch=FetchType.LAZY) @MapsId("ticketId") @JoinColumn(name="ticket_id") private Ticket ticket;
    @ManyToOne(fetch=FetchType.LAZY) @MapsId("userId") @JoinColumn(name="user_id") private AppUser user;
    @Column(name="created_at", nullable=false) private Instant createdAt = Instant.now();
    @Column(nullable=false) private boolean muted;
    protected TicketFollower() {}
    public TicketFollower(Ticket ticket, AppUser user){this.ticket=ticket;this.user=user;this.id=new Id(ticket.getId(),user.getId());}
    public boolean isMuted(){return muted;} public void setMuted(boolean value){muted=value;}
    @Embeddable public record Id(Long ticketId, Long userId) {}
}
