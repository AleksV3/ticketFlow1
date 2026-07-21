package com.ticketflow1.ticketing.ticketconfig;

import com.ticketflow1.ticketing.common.Auditable;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity @Table(name = "subtype_field_option")
public class SubtypeFieldOption extends Auditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "field_definition_id") private SubtypeFieldDefinition fieldDefinition;
    @Column(nullable = false, length = 50) private String key;
    @Column(nullable = false, length = 120) private String label;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Version @Column(nullable = false) private long version;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected SubtypeFieldOption() {}
    public SubtypeFieldOption(SubtypeFieldDefinition fieldDefinition,String key,String label,int sortOrder){this.fieldDefinition=fieldDefinition;
        this.key=key;this.label=label;this.sortOrder=sortOrder;}
    public Long getId(){return id;} public SubtypeFieldDefinition getFieldDefinition(){return fieldDefinition;} public String getKey(){return key;}
    public String getLabel(){return label;} public boolean isActive(){return active;} public int getSortOrder(){return sortOrder;}
    public long getVersion(){return version;} public void update(String label,int sortOrder){this.label=label;this.sortOrder=sortOrder;}
    public void setActive(boolean active){this.active=active;}
}
