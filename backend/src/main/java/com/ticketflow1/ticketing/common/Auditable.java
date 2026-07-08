package com.ticketflow1.ticketing.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Row-level "who touched this last" metadata for mutable tables. Append-only
 * tables (audit_log, status_history in later phases) must NOT extend this —
 * their rows are never updated and they already carry actor + insert time.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class Auditable {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Null = the row was written by a migration/seed, not a logged-in user. */
    @LastModifiedBy
    @Column(name = "updated_by_id")
    private Long updatedById;

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedById() {
        return updatedById;
    }

    /**
     * Dirty the row so Hibernate issues an UPDATE and the auditing listener
     * re-stamps it. Needed when only a @ManyToMany collection changed —
     * collection writes hit the join table and don't dirty the owning row,
     * so no @PreUpdate fires and the stamp would silently stay stale.
     */
    public void touchForAudit() {
        this.updatedAt = Instant.now();
    }
}
