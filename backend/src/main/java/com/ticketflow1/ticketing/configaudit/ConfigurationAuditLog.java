package com.ticketflow1.ticketing.configaudit;

import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.user.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "configuration_audit_log")
public class ConfigurationAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "organization_id") private Organization organization;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "actor_id") private AppUser actor;
    @Column(name = "target_type", nullable = false, length = 40) private String targetType;
    @Column(name = "target_id", nullable = false) private Long targetId;
    @Column(nullable = false, length = 40) private String action;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "old_value", columnDefinition = "jsonb") private String oldValue;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "new_value", columnDefinition = "jsonb") private String newValue;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected ConfigurationAuditLog() {}
    public ConfigurationAuditLog(Organization organization, AppUser actor, String targetType, Long targetId,
            String action, String oldValue, String newValue) {
        this.organization = organization; this.actor = actor; this.targetType = targetType; this.targetId = targetId;
        this.action = action; this.oldValue = oldValue; this.newValue = newValue;
    }
    public Long getId() { return id; }
    public Organization getOrganization() { return organization; }
    public AppUser getActor() { return actor; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getAction() { return action; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public Instant getCreatedAt() { return createdAt; }
}
