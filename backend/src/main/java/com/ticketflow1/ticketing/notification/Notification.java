package com.ticketflow1.ticketing.notification;

import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.user.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "notification")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "recipient_id") private AppUser recipient;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "ticket_id") private Ticket ticket;
    @Column(name = "event_type", nullable = false, length = 60) private String eventType;
    @Column(nullable = false, length = 255) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String message;
    @Column(name = "read_at") private Instant readAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected Notification() {}
    public Notification(AppUser recipient, Ticket ticket, String eventType, String title, String message) {
        this.recipient = recipient; this.ticket = ticket; this.eventType = eventType; this.title = title; this.message = message;
    }
    public Long getId(){return id;} public AppUser getRecipient(){return recipient;} public Ticket getTicket(){return ticket;}
    public String getEventType(){return eventType;} public String getTitle(){return title;} public String getMessage(){return message;}
    public Instant getReadAt(){return readAt;} public Instant getCreatedAt(){return createdAt;} public void markRead(){readAt=Instant.now();}
}
