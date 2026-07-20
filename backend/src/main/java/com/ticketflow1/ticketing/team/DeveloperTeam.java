package com.ticketflow1.ticketing.team;

import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.user.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "developer_team")
public class DeveloperTeam {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 200) private String name;
    @Column(columnDefinition = "TEXT") private String description;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "leader_id") private AppUser leader;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by_id") private AppUser createdBy;
    @ManyToMany(fetch = FetchType.LAZY) @JoinTable(name = "developer_team_member", joinColumns = @JoinColumn(name = "team_id"), inverseJoinColumns = @JoinColumn(name = "user_id")) private Set<AppUser> developers = new LinkedHashSet<>();
    @ManyToMany(fetch = FetchType.LAZY) @JoinTable(name = "developer_team_ticket", joinColumns = @JoinColumn(name = "team_id"), inverseJoinColumns = @JoinColumn(name = "ticket_id")) private Set<Ticket> tickets = new LinkedHashSet<>();
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected DeveloperTeam() {}
    public DeveloperTeam(String name, String description, AppUser leader, AppUser createdBy) { this.name=name; this.description=description; this.leader=leader; this.createdBy=createdBy; }
    public Long getId(){return id;} public String getName(){return name;} public String getDescription(){return description;} public AppUser getLeader(){return leader;} public AppUser getCreatedBy(){return createdBy;} public Set<AppUser> getDevelopers(){return developers;} public Set<Ticket> getTickets(){return tickets;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
    public void update(String name,String description,AppUser leader,Set<AppUser> developers,Set<Ticket> tickets){this.name=name;this.description=description;this.leader=leader;this.developers.clear();this.developers.addAll(developers);this.tickets.clear();this.tickets.addAll(tickets);}
    public void addTicket(Ticket ticket){tickets.add(ticket);}
    public void removeTicket(Ticket ticket){tickets.remove(ticket);}
}
