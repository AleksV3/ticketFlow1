package com.ticketflow1.ticketing.ticketconfig;

import com.ticketflow1.ticketing.common.Auditable;
import com.ticketflow1.ticketing.workflow.TicketType;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "ticket_subtype")
public class TicketSubtype extends Auditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ticket_type_id") private TicketType ticketType;
    @Column(nullable = false, length = 50) private String key;
    @Column(nullable = false, length = 120) private String name;
    @Column(length = 1000) private String description;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Version @Column(nullable = false) private long version;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected TicketSubtype() {}
    public TicketSubtype(TicketType ticketType, String key, String name, String description, int sortOrder) {
        this.ticketType=ticketType; this.key=key; this.name=name; this.description=description; this.sortOrder=sortOrder;
    }
    public Long getId(){return id;} public TicketType getTicketType(){return ticketType;} public String getKey(){return key;}
    public String getName(){return name;} public String getDescription(){return description;} public boolean isActive(){return active;}
    public int getSortOrder(){return sortOrder;} public long getVersion(){return version;} public Instant getCreatedAt(){return createdAt;}
    public void update(String name,String description,int sortOrder){this.name=name;this.description=description;this.sortOrder=sortOrder;}
    public void setActive(boolean active){this.active=active;}
}
