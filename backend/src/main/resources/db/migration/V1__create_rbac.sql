-- V1: RBAC foundation. Permission is the fixed, code-owned action catalog;
-- role is a configurable bundle of permissions (data-model.md). Fixed value
-- sets (party) are TEXT + CHECK, not native ENUM.

CREATE TABLE permission (
    id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    key VARCHAR(60) NOT NULL UNIQUE
);

-- organization_id: NULL = global template (TicketFlow1-party roles are
-- always global; CLIENT-party roles are cloned per organization on org
-- creation via clone_org_templates(), defined in V2). is_template marks the
-- seed rows every organization's CLIENT-party roles are cloned from.
-- updated_at / updated_by_id: row-level "who touched this last" metadata on
-- every mutable table (stamped by JPA auditing; NULL updated_by_id = the row
-- was written by a migration/seed). Append-only tables in later migrations
-- (audit_log, status_history) deliberately don't get these — their rows are
-- never updated. permission and role_permission are exempt too: permission is
-- the fixed code-owned catalog, and role_permission edits stamp the role row.
CREATE TABLE role (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    party           VARCHAR(12)  NOT NULL CHECK (party IN ('CLIENT', 'TICKETFLOW1')),
    organization_id BIGINT,
    is_template     BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by_id   BIGINT,
    UNIQUE (organization_id, name)
);

CREATE TABLE role_permission (
    role_id       BIGINT NOT NULL REFERENCES role (id),
    permission_id BIGINT NOT NULL REFERENCES permission (id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE organization (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(200) NOT NULL UNIQUE,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by_id BIGINT
);

ALTER TABLE role ADD CONSTRAINT fk_role_organization
    FOREIGN KEY (organization_id) REFERENCES organization (id);

-- "user" is a reserved word in Postgres, so the table is "app_user".
-- party is structural (never role-derived); role_id's role.party must match.
CREATE TABLE app_user (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(200) NOT NULL,
    party           VARCHAR(12)  NOT NULL CHECK (party IN ('CLIENT', 'TICKETFLOW1')),
    role_id         BIGINT       NOT NULL REFERENCES role (id),
    organization_id BIGINT       REFERENCES organization (id),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by_id   BIGINT       REFERENCES app_user (id)
);

-- role/organization are created before app_user exists, so their
-- updated_by_id FKs are added here (same pattern as fk_role_organization).
ALTER TABLE role ADD CONSTRAINT fk_role_updated_by
    FOREIGN KEY (updated_by_id) REFERENCES app_user (id);
ALTER TABLE organization ADD CONSTRAINT fk_organization_updated_by
    FOREIGN KEY (updated_by_id) REFERENCES app_user (id);

CREATE INDEX idx_app_user_organization_id ON app_user (organization_id);
CREATE INDEX idx_role_organization_id ON role (organization_id);

-- Fixed permission catalog (FR-008). Code-owned, not editable at runtime.
-- TICKET_CANCEL is split out from TICKET_TRANSITION so cancelling a ticket
-- can be restricted more tightly than ordinary transitions (only Admin/
-- TicketFlow1 Manager hold it by default, not every TicketFlow1 User).
INSERT INTO permission (key) VALUES
    ('TICKET_READ'),
    ('TICKET_CREATE'),
    ('TICKET_UPDATE'),
    ('TICKET_TRANSITION'),
    ('TICKET_CANCEL'),
    ('PROPOSAL_APPROVE'),
    ('COMMENT_PUBLIC_WRITE'),
    ('COMMENT_INTERNAL_WRITE'),
    ('USER_MANAGE'),
    ('ROLE_MANAGE'),
    ('TYPE_MANAGE'),
    ('WORKFLOW_MANAGE');

-- Default role templates (FR-009). TicketFlow1-party templates are used
-- directly (one vendor, no cloning); CLIENT-party templates are cloned per
-- organization by clone_org_templates() and never assigned to a user directly.
INSERT INTO role (name, party, organization_id, is_template) VALUES
    ('Admin', 'TICKETFLOW1', NULL, TRUE),
    ('TicketFlow1 User', 'TICKETFLOW1', NULL, TRUE),
    ('TicketFlow1 Manager', 'TICKETFLOW1', NULL, TRUE),
    ('Client User', 'CLIENT', NULL, TRUE),
    ('Client Approver', 'CLIENT', NULL, TRUE);

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.name = 'Admin' AND p.key IN
    ('TICKET_READ', 'TICKET_CREATE', 'TICKET_UPDATE', 'TICKET_TRANSITION', 'TICKET_CANCEL',
     'COMMENT_PUBLIC_WRITE', 'COMMENT_INTERNAL_WRITE',
     'USER_MANAGE', 'ROLE_MANAGE', 'TYPE_MANAGE', 'WORKFLOW_MANAGE');

-- TicketFlow1 Manager gets TICKET_CANCEL; TicketFlow1 User does not (that's
-- the whole point of splitting the permission out).
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.name IN ('TicketFlow1 User', 'TicketFlow1 Manager') AND p.key IN
    ('TICKET_READ', 'TICKET_CREATE', 'TICKET_UPDATE', 'TICKET_TRANSITION',
     'COMMENT_PUBLIC_WRITE', 'COMMENT_INTERNAL_WRITE');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.name = 'TicketFlow1 Manager' AND p.key = 'TICKET_CANCEL';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.name = 'Client User' AND p.key IN
    ('TICKET_READ', 'TICKET_CREATE', 'TICKET_UPDATE', 'TICKET_TRANSITION',
     'COMMENT_PUBLIC_WRITE');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.name = 'Client Approver' AND p.key IN
    ('TICKET_READ', 'TICKET_CREATE', 'TICKET_UPDATE', 'TICKET_TRANSITION',
     'PROPOSAL_APPROVE', 'COMMENT_PUBLIC_WRITE');

-- Bootstrap admin so login is verifiable this phase (global role, no org).
-- Credentials: admin@ticketflow1.demo / admin123 (BCrypt hash below).
INSERT INTO app_user (email, password_hash, display_name, party, role_id, organization_id)
SELECT 'admin@ticketflow1.demo',
       '$2a$10$ssUxK.6FCVlYURe4987adu/r5Ccy4B0rSfDOCsaiQO/rswNRW/Qje',
       'TicketFlow1 Admin', 'TICKETFLOW1', r.id, NULL
FROM role r WHERE r.name = 'Admin';
