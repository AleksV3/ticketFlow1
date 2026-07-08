package com.ticketflow1.ticketing.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The fixed, code-owned action catalog (FR-008). Rows are seeded once (V1)
 * and never created or edited at runtime — only role-permission bundles are
 * configurable, not the catalog itself.
 */
@Entity
@Table(name = "permission")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String key;

    protected Permission() {
        // JPA
    }

    public Permission(String key) {
        this.key = key;
    }

    public Long getId() {
        return id;
    }

    public String getKey() {
        return key;
    }
}
