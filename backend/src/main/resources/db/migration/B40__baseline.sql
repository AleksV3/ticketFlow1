-- Generated Flyway baseline for new, empty databases.

-- Do not edit manually: regenerate from V*.sql when the schema changes.

-- Source migration: V1__create_rbac.sql
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
    version         BIGINT       NOT NULL DEFAULT 0,
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
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
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

-- Intentionally no users or credentials here. Fixed demo accounts live only
-- in db/demo-migration/V8__seed_demo_data.sql and are enabled by the demo
-- Spring profile. Production deployments bootstrap their first user through
-- an environment-specific operational process.

-- Source migration: V2__create_workflow_model.sql
-- V2: workflow/type configuration model. Global template workflows + ticket
-- types are seeded here, then cloned per client Organization so ticket
-- creation can resolve organization-owned definitions immediately.

CREATE TABLE workflow (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    organization_id BIGINT REFERENCES organization (id),
    canvas_layout   TEXT,
    version         BIGINT       NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by_id   BIGINT REFERENCES app_user (id),
    UNIQUE (organization_id, name)
);

CREATE TABLE workflow_state (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workflow_id   BIGINT       NOT NULL REFERENCES workflow (id) ON DELETE CASCADE,
    key           VARCHAR(40)  NOT NULL,
    name          VARCHAR(100),
    is_initial    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_terminal   BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order    INTEGER      NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by_id BIGINT       REFERENCES app_user (id),
    UNIQUE (workflow_id, key)
);

CREATE UNIQUE INDEX ux_workflow_state_one_initial
    ON workflow_state (workflow_id)
    WHERE is_initial;

CREATE TABLE workflow_transition (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workflow_id            BIGINT       NOT NULL REFERENCES workflow (id) ON DELETE CASCADE,
    from_state_id          BIGINT       NOT NULL REFERENCES workflow_state (id) ON DELETE CASCADE,
    to_state_id            BIGINT       NOT NULL REFERENCES workflow_state (id) ON DELETE CASCADE,
    required_permission_id BIGINT       NOT NULL REFERENCES permission (id),
    required_party         VARCHAR(12)  CHECK (required_party IN ('CLIENT', 'TICKETFLOW1')),
    responsibility_after   VARCHAR(12)  CHECK (responsibility_after IN ('CLIENT', 'TICKETFLOW1')),
    operation_kind         VARCHAR(24)  NOT NULL DEFAULT 'STANDARD'
        CHECK (operation_kind IN ('STANDARD','PROPOSAL_CREATE','PROPOSAL_APPROVE','PROPOSAL_REJECT',
                                 'WORKFLOW_APPROVE','WORKFLOW_REJECT','CORRECTION_RETURN','CLIENT_ACCEPT','CLIENT_REJECT')),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by_id          BIGINT       REFERENCES app_user (id),
    UNIQUE (workflow_id, from_state_id, to_state_id)
);

CREATE TABLE ticket_type (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    key               VARCHAR(40)  NOT NULL,
    name              VARCHAR(100) NOT NULL,
    workflow_id       BIGINT       NOT NULL REFERENCES workflow (id),
    organization_id   BIGINT       REFERENCES organization (id),
    is_template       BOOLEAN      NOT NULL DEFAULT FALSE,
    requires_proposal BOOLEAN      NOT NULL DEFAULT FALSE,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order        INTEGER      NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    capability        VARCHAR(20)  NOT NULL DEFAULT 'STANDARD'
        CHECK (capability IN ('STANDARD', 'DEFECT_SLA')),
    version           BIGINT       NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by_id     BIGINT       REFERENCES app_user (id),
    UNIQUE (organization_id, key)
);

CREATE INDEX idx_workflow_organization_id ON workflow (organization_id);
CREATE INDEX idx_workflow_state_workflow_id ON workflow_state (workflow_id);
CREATE INDEX idx_workflow_transition_workflow_id ON workflow_transition (workflow_id);
CREATE INDEX idx_ticket_type_organization_id ON ticket_type (organization_id);

INSERT INTO workflow (name, organization_id) VALUES
    ('Change Request Workflow', NULL),
    ('Task Workflow', NULL),
    ('Defect Workflow', NULL);

INSERT INTO workflow_state (workflow_id, key, is_initial, is_terminal, sort_order)
SELECT w.id, s.key, s.is_initial, s.is_terminal, s.sort_order
FROM workflow w
JOIN (
    VALUES
        ('Change Request Workflow', 'SUBMITTED', TRUE, FALSE, 10),
        ('Change Request Workflow', 'ANALYSIS', FALSE, FALSE, 20),
        ('Change Request Workflow', 'PROPOSAL', FALSE, FALSE, 30),
        ('Change Request Workflow', 'PROPOSAL_REJECTED', FALSE, FALSE, 40),
        ('Change Request Workflow', 'PROPOSAL_APPROVED', FALSE, FALSE, 50),
        ('Change Request Workflow', 'DEVELOPMENT', FALSE, FALSE, 60),
        ('Change Request Workflow', 'FIRST_OCCURRENCE_TESTING', FALSE, FALSE, 70),
        ('Change Request Workflow', 'USER_ACCEPTANCE_TESTING', FALSE, FALSE, 80),
        ('Change Request Workflow', 'READY_FOR_PRODUCTION', FALSE, FALSE, 90),
        ('Change Request Workflow', 'IN_PRODUCTION', FALSE, FALSE, 100),
        ('Change Request Workflow', 'CLOSED', FALSE, TRUE, 110),
        ('Change Request Workflow', 'CANCELLED', FALSE, TRUE, 120),
        ('Task Workflow', 'SUBMITTED', TRUE, FALSE, 10),
        ('Task Workflow', 'ANALYSIS', FALSE, FALSE, 20),
        ('Task Workflow', 'DEVELOPMENT', FALSE, FALSE, 30),
        ('Task Workflow', 'FIRST_OCCURRENCE_TESTING', FALSE, FALSE, 40),
        ('Task Workflow', 'USER_ACCEPTANCE_TESTING', FALSE, FALSE, 50),
        ('Task Workflow', 'READY_FOR_PRODUCTION', FALSE, FALSE, 60),
        ('Task Workflow', 'IN_PRODUCTION', FALSE, FALSE, 70),
        ('Task Workflow', 'CLOSED', FALSE, TRUE, 80),
        ('Task Workflow', 'CANCELLED', FALSE, TRUE, 90),
        ('Defect Workflow', 'REPORTED', TRUE, FALSE, 10),
        ('Defect Workflow', 'ANALYSIS', FALSE, FALSE, 20),
        ('Defect Workflow', 'FIX_IN_PROGRESS', FALSE, FALSE, 30),
        ('Defect Workflow', 'CLIENT_CONFIRMATION', FALSE, FALSE, 40),
        ('Defect Workflow', 'CLOSED', FALSE, TRUE, 50),
        ('Defect Workflow', 'CANCELLED', FALSE, TRUE, 60)
) AS s(workflow_name, key, is_initial, is_terminal, sort_order)
    ON s.workflow_name = w.name;

INSERT INTO workflow_transition (
    workflow_id, from_state_id, to_state_id, required_permission_id, required_party, responsibility_after
)
SELECT w.id, fs.id, ts.id, p.id, t.required_party, t.responsibility_after
FROM (
    VALUES
        ('Change Request Workflow', 'SUBMITTED', 'ANALYSIS', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Change Request Workflow', 'SUBMITTED', 'CANCELLED', 'TICKET_CANCEL', 'TICKETFLOW1', NULL),
        ('Change Request Workflow', 'ANALYSIS', 'PROPOSAL', 'TICKET_TRANSITION', 'TICKETFLOW1', 'CLIENT'),
        ('Change Request Workflow', 'ANALYSIS', 'CANCELLED', 'TICKET_CANCEL', 'TICKETFLOW1', NULL),
        ('Change Request Workflow', 'PROPOSAL', 'PROPOSAL_APPROVED', 'PROPOSAL_APPROVE', 'CLIENT', 'TICKETFLOW1'),
        ('Change Request Workflow', 'PROPOSAL', 'PROPOSAL_REJECTED', 'PROPOSAL_APPROVE', 'CLIENT', 'TICKETFLOW1'),
        ('Change Request Workflow', 'PROPOSAL_REJECTED', 'ANALYSIS', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Change Request Workflow', 'PROPOSAL_REJECTED', 'CANCELLED', 'TICKET_CANCEL', 'TICKETFLOW1', NULL),
        ('Change Request Workflow', 'PROPOSAL_APPROVED', 'DEVELOPMENT', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Change Request Workflow', 'DEVELOPMENT', 'FIRST_OCCURRENCE_TESTING', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Change Request Workflow', 'FIRST_OCCURRENCE_TESTING', 'USER_ACCEPTANCE_TESTING', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Change Request Workflow', 'USER_ACCEPTANCE_TESTING', 'READY_FOR_PRODUCTION', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Change Request Workflow', 'READY_FOR_PRODUCTION', 'IN_PRODUCTION', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Change Request Workflow', 'IN_PRODUCTION', 'CLOSED', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Task Workflow', 'SUBMITTED', 'ANALYSIS', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Task Workflow', 'SUBMITTED', 'CANCELLED', 'TICKET_CANCEL', 'TICKETFLOW1', NULL),
        ('Task Workflow', 'ANALYSIS', 'DEVELOPMENT', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Task Workflow', 'ANALYSIS', 'CANCELLED', 'TICKET_CANCEL', 'TICKETFLOW1', NULL),
        ('Task Workflow', 'DEVELOPMENT', 'FIRST_OCCURRENCE_TESTING', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Task Workflow', 'FIRST_OCCURRENCE_TESTING', 'USER_ACCEPTANCE_TESTING', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Task Workflow', 'USER_ACCEPTANCE_TESTING', 'READY_FOR_PRODUCTION', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Task Workflow', 'READY_FOR_PRODUCTION', 'IN_PRODUCTION', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Task Workflow', 'IN_PRODUCTION', 'CLOSED', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Defect Workflow', 'REPORTED', 'ANALYSIS', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Defect Workflow', 'REPORTED', 'CANCELLED', 'TICKET_CANCEL', 'TICKETFLOW1', NULL),
        ('Defect Workflow', 'ANALYSIS', 'FIX_IN_PROGRESS', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Defect Workflow', 'ANALYSIS', 'CANCELLED', 'TICKET_CANCEL', 'TICKETFLOW1', NULL),
        ('Defect Workflow', 'FIX_IN_PROGRESS', 'CLIENT_CONFIRMATION', 'TICKET_TRANSITION', 'TICKETFLOW1', 'CLIENT'),
        ('Defect Workflow', 'CLIENT_CONFIRMATION', 'CLOSED', 'TICKET_TRANSITION', 'CLIENT', NULL),
        ('Defect Workflow', 'CLIENT_CONFIRMATION', 'FIX_IN_PROGRESS', 'TICKET_TRANSITION', 'CLIENT', 'TICKETFLOW1')
) AS t(workflow_name, from_key, to_key, permission_key, required_party, responsibility_after)
JOIN workflow w ON w.name = t.workflow_name
JOIN workflow_state fs ON fs.workflow_id = w.id AND fs.key = t.from_key
JOIN workflow_state ts ON ts.workflow_id = w.id AND ts.key = t.to_key
JOIN permission p ON p.key = t.permission_key;

INSERT INTO ticket_type (key, name, workflow_id, organization_id, is_template, requires_proposal)
SELECT tt.key, tt.name, w.id, NULL, TRUE, tt.requires_proposal
FROM (
    VALUES
        ('CHANGE_REQUEST', 'Change Request', 'Change Request Workflow', TRUE),
        ('TASK', 'Task', 'Task Workflow', FALSE),
        ('DEFECT', 'Defect', 'Defect Workflow', FALSE)
) AS tt(key, name, workflow_name, requires_proposal)
JOIN workflow w ON w.name = tt.workflow_name;

-- Source migration: V3__create_ticket.sql
-- V3: operational ticket core. ticket is mutable/audited; status_history and
-- audit_log are append-only event tables and intentionally do not carry
-- updated_at / updated_by_id.

CREATE SEQUENCE ticket_key_seq START WITH 1000 INCREMENT BY 1;

CREATE TABLE ticket (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_key             VARCHAR(20)   NOT NULL UNIQUE,
    ticket_type_id         BIGINT        NOT NULL REFERENCES ticket_type (id),
    current_state_id       BIGINT        NOT NULL REFERENCES workflow_state (id),
    priority               VARCHAR(10)   NOT NULL DEFAULT 'MEDIUM'
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    severity               VARCHAR(6)
        CHECK (severity IN ('SEV_1', 'SEV_2', 'SEV_3', 'SEV_4')),
    title                  VARCHAR(300)  NOT NULL,
    description            TEXT          NOT NULL,
    organization_id        BIGINT        NOT NULL REFERENCES organization (id),
    business_owner_id      BIGINT        NOT NULL REFERENCES app_user (id),
    ticket_lead_id         BIGINT        REFERENCES app_user (id),
    assigned_team          VARCHAR(100),
    current_responsibility VARCHAR(12)   NOT NULL DEFAULT 'TICKETFLOW1'
        CHECK (current_responsibility IN ('CLIENT', 'TICKETFLOW1')),
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by_id          BIGINT        REFERENCES app_user (id),
    closed_at              TIMESTAMPTZ,
    response_due_at        TIMESTAMPTZ,
    first_info_due_at      TIMESTAMPTZ,
    next_update_due_at     TIMESTAMPTZ,
    responded_at           TIMESTAMPTZ,
    first_info_at          TIMESTAMPTZ
);

CREATE INDEX idx_ticket_organization_id ON ticket (organization_id);
CREATE INDEX idx_ticket_business_owner_id ON ticket (business_owner_id);
CREATE INDEX idx_ticket_ticket_lead_id ON ticket (ticket_lead_id);
CREATE INDEX idx_ticket_current_state_id ON ticket (current_state_id);
CREATE INDEX idx_ticket_type_id ON ticket (ticket_type_id);

CREATE TABLE audit_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id   BIGINT       NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    actor_id    BIGINT       NOT NULL REFERENCES app_user (id),
    action      VARCHAR(40)  NOT NULL CHECK (
        action IN (
            'TICKET_CREATED',
            'STATUS_CHANGED',
            'ASSIGNEE_CHANGED',
            'COMMENT_ADDED',
            'PROPOSAL_CREATED',
            'PROPOSAL_APPROVED',
            'PROPOSAL_REJECTED',
            'SEVERITY_CHANGED',
            'PRIORITY_CHANGED',
            'ATTACHMENT_ADDED',
            'DYNAMIC_FIELDS_CAPTURED',
            'CORRECTION_RETURN',
            'WORKFLOW_APPROVED',
            'WORKFLOW_REJECTED',
            'TICKET_UPDATED',
            'CONFIG_CHANGED'
        )
    ),
    field_name  VARCHAR(100),
    old_value   TEXT,
    new_value   TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_ticket_created_at ON audit_log (ticket_id, created_at);

CREATE TABLE status_history (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id      BIGINT       NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    from_state_id  BIGINT       REFERENCES workflow_state (id),
    to_state_id    BIGINT       NOT NULL REFERENCES workflow_state (id),
    changed_by_id  BIGINT       NOT NULL REFERENCES app_user (id),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_status_history_ticket_created_at ON status_history (ticket_id, created_at);

-- Source migration: V5__create_comment_and_attachment.sql
-- Phase 4 communication records. Attachments are metadata references only;
-- no file bytes are stored by the MVP.

CREATE TABLE comment (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id     BIGINT       NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    author_id     BIGINT       NOT NULL REFERENCES app_user (id),
    body          TEXT         NOT NULL CHECK (length(btrim(body)) BETWEEN 1 AND 10000),
    visibility    VARCHAR(10)  NOT NULL CHECK (visibility IN ('INTERNAL', 'PUBLIC')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by_id BIGINT       REFERENCES app_user (id)
);

CREATE INDEX idx_comment_ticket_created_at ON comment (ticket_id, created_at);

CREATE TABLE attachment (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id     BIGINT        NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    uploaded_by_id BIGINT       NOT NULL REFERENCES app_user (id),
    file_name     VARCHAR(255)  NOT NULL CHECK (length(btrim(file_name)) BETWEEN 1 AND 255),
    content_type  VARCHAR(100)  NOT NULL CHECK (
        length(btrim(content_type)) BETWEEN 3 AND 100
        AND position('/' IN content_type) > 1
    ),
    size_bytes    BIGINT        NOT NULL CHECK (size_bytes >= 0),
    storage_path  VARCHAR(500),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by_id BIGINT        REFERENCES app_user (id)
);

CREATE INDEX idx_attachment_ticket_created_at ON attachment (ticket_id, created_at);

-- Source migration: V5_1__grant_comment_internal_read.sql
-- COMMENT_INTERNAL_READ was documented from the start but omitted from V1.
-- Add it forward-only because deployed Flyway migrations must never be edited.
INSERT INTO permission (key)
VALUES ('COMMENT_INTERNAL_READ')
ON CONFLICT (key) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.key = 'COMMENT_INTERNAL_READ'
WHERE r.party = 'TICKETFLOW1'
  AND r.name IN ('Admin', 'TicketFlow1 User', 'TicketFlow1 Manager')
ON CONFLICT DO NOTHING;

-- Source migration: V6__create_change_proposal.sql
-- Phase 5 change proposals. Proposal decisions are mutable and use optimistic
-- locking; a partial unique index closes the race between two proposal creates.

CREATE TABLE change_proposal (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id               BIGINT        NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    description             TEXT          NOT NULL CHECK (length(btrim(description)) > 0),
    estimated_delivery_date DATE,
    effort_estimate         VARCHAR(100),
    status                  VARCHAR(10)   NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_by_id           BIGINT        NOT NULL REFERENCES app_user (id),
    decided_by_id           BIGINT        REFERENCES app_user (id),
    decided_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by_id           BIGINT        REFERENCES app_user (id),
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_change_proposal_decision CHECK (
        (status = 'PENDING' AND decided_by_id IS NULL AND decided_at IS NULL)
        OR
        (status IN ('APPROVED', 'REJECTED') AND decided_by_id IS NOT NULL AND decided_at IS NOT NULL)
    )
);

CREATE INDEX idx_change_proposal_ticket_latest
    ON change_proposal (ticket_id, created_at DESC, id DESC);

CREATE UNIQUE INDEX uq_change_proposal_one_pending_per_ticket
    ON change_proposal (ticket_id)
    WHERE status = 'PENDING';

-- Final operation-kind values for the original workflow transitions.
UPDATE workflow_transition wt
SET operation_kind = CASE
    WHEN fs.key = 'ANALYSIS' AND ts.key = 'PROPOSAL' THEN 'PROPOSAL_CREATE'
    WHEN fs.key = 'PROPOSAL' AND ts.key = 'PROPOSAL_APPROVED' THEN 'PROPOSAL_APPROVE'
    WHEN fs.key = 'PROPOSAL' AND ts.key = 'PROPOSAL_REJECTED' THEN 'PROPOSAL_REJECT'
    ELSE 'STANDARD'
END
FROM workflow_state fs, workflow_state ts
WHERE fs.id = wt.from_state_id AND ts.id = wt.to_state_id;

-- clone_org_templates copies transitions without naming operation_kind. This
-- trigger preserves protection for future organization clones as well.
CREATE FUNCTION assign_proposal_operation_kind() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE from_key VARCHAR(40); to_key VARCHAR(40);
BEGIN
    SELECT key INTO from_key FROM workflow_state WHERE id = NEW.from_state_id;
    SELECT key INTO to_key FROM workflow_state WHERE id = NEW.to_state_id;
    NEW.operation_kind := CASE
        WHEN from_key = 'ANALYSIS' AND to_key = 'PROPOSAL' THEN 'PROPOSAL_CREATE'
        WHEN from_key = 'PROPOSAL' AND to_key = 'PROPOSAL_APPROVED' THEN 'PROPOSAL_APPROVE'
        WHEN from_key = 'PROPOSAL' AND to_key = 'PROPOSAL_REJECTED' THEN 'PROPOSAL_REJECT'
        ELSE COALESCE(NEW.operation_kind, 'STANDARD')
    END;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_workflow_transition_operation_kind
BEFORE INSERT ON workflow_transition
FOR EACH ROW EXECUTE FUNCTION assign_proposal_operation_kind();

-- Partial indexes keep the moving SLA queries focused on unfinished obligations.
CREATE INDEX idx_ticket_sla_response_unmet
    ON ticket (response_due_at, organization_id)
    WHERE response_due_at IS NOT NULL AND responded_at IS NULL;

CREATE INDEX idx_ticket_sla_first_info_unmet
    ON ticket (first_info_due_at, organization_id)
    WHERE first_info_due_at IS NOT NULL AND first_info_at IS NULL;

CREATE INDEX idx_ticket_sla_next_update
    ON ticket (next_update_due_at, organization_id)
    WHERE next_update_due_at IS NOT NULL;

-- Configuration auditing is part of the final schema.
CREATE TABLE configuration_audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id BIGINT REFERENCES organization(id),
    actor_id BIGINT NOT NULL REFERENCES app_user(id),
    target_type VARCHAR(40) NOT NULL,
    target_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL,
    old_value JSONB,
    new_value JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_configuration_audit_scope_created
    ON configuration_audit_log (organization_id, created_at DESC);

-- Source migration: V9__allow_multiple_user_roles.sql
CREATE TABLE app_user_role (
    user_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES role (id),
    PRIMARY KEY (user_id, role_id)
);

INSERT INTO app_user_role (user_id, role_id)
SELECT id, role_id FROM app_user;

-- Source migration: V10__ticket_team_assignment.sql
INSERT INTO permission (key) VALUES ('TICKET_ASSIGN');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.organization_id IS NULL
  AND r.party = 'TICKETFLOW1'
  AND r.name IN ('Admin', 'TicketFlow1 Manager')
  AND p.key = 'TICKET_ASSIGN';

CREATE TABLE ticket_developer (
    ticket_id BIGINT NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user (id),
    PRIMARY KEY (ticket_id, user_id)
);

-- Source migration: V12__create_internal_organization.sql
INSERT INTO organization (name)
SELECT 'TicketFlow1 Internal'
WHERE NOT EXISTS (SELECT 1 FROM organization WHERE lower(name) = lower('TicketFlow1 Internal'));

-- Source migration: V13__create_developer_teams.sql
CREATE TABLE developer_team (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    leader_id BIGINT NOT NULL REFERENCES app_user (id),
    created_by_id BIGINT NOT NULL REFERENCES app_user (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE developer_team_member (
    team_id BIGINT NOT NULL REFERENCES developer_team (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user (id),
    PRIMARY KEY (team_id, user_id)
);

CREATE TABLE developer_team_ticket (
    team_id BIGINT NOT NULL REFERENCES developer_team (id) ON DELETE CASCADE,
    ticket_id BIGINT NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL,
    PRIMARY KEY (team_id, sort_order)
);

-- Source migration: V16__create_subtype_forms.sql
-- V16: bounded configurable subtype forms. Definitions are data in a fixed schema;
-- no runtime DDL, scripts, expressions, HTML, SQL, or custom field kinds.

CREATE TABLE ticket_subtype (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_type_id BIGINT       NOT NULL REFERENCES ticket_type (id) ON DELETE RESTRICT,
    key            VARCHAR(50)  NOT NULL,
    name           VARCHAR(120) NOT NULL,
    description    VARCHAR(1000),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order     INTEGER      NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by_id  BIGINT       REFERENCES app_user (id),
    CONSTRAINT ck_ticket_subtype_key CHECK (key ~ '^[A-Z][A-Z0-9_]{1,49}$'),
    UNIQUE (ticket_type_id, key)
);

CREATE INDEX idx_ticket_subtype_type_order
    ON ticket_subtype (ticket_type_id, active, sort_order, id);

CREATE TABLE subtype_field_definition (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subtype_id     BIGINT       NOT NULL REFERENCES ticket_subtype (id) ON DELETE RESTRICT,
    key            VARCHAR(50)  NOT NULL,
    label          VARCHAR(120) NOT NULL,
    help_text      VARCHAR(1000),
    field_kind     VARCHAR(20)  NOT NULL CHECK (field_kind IN (
        'SHORT_TEXT', 'LONG_TEXT', 'INTEGER', 'DECIMAL', 'DATE', 'BOOLEAN',
        'SINGLE_SELECT', 'MULTI_SELECT', 'USER_REFERENCE', 'TEAM_REFERENCE'
    )),
    required       BOOLEAN      NOT NULL DEFAULT FALSE,
    visibility     VARCHAR(10)  NOT NULL DEFAULT 'INTERNAL'
        CHECK (visibility IN ('PUBLIC', 'INTERNAL')),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order     INTEGER      NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    min_length     INTEGER,
    max_length     INTEGER,
    min_number     NUMERIC(19,4),
    max_number     NUMERIC(19,4),
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by_id  BIGINT       REFERENCES app_user (id),
    CONSTRAINT ck_subtype_field_key CHECK (key ~ '^[a-z][a-z0-9_]{1,49}$'),
    CONSTRAINT ck_subtype_field_lengths CHECK (
        (min_length IS NULL OR min_length >= 0) AND
        (max_length IS NULL OR max_length > 0) AND
        (min_length IS NULL OR max_length IS NULL OR min_length <= max_length)
    ),
    CONSTRAINT ck_subtype_field_numbers CHECK (
        min_number IS NULL OR max_number IS NULL OR min_number <= max_number
    ),
    UNIQUE (subtype_id, key)
);

CREATE INDEX idx_subtype_field_definition_order
    ON subtype_field_definition (subtype_id, active, sort_order, id);

CREATE TABLE subtype_field_option (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    field_definition_id BIGINT       NOT NULL REFERENCES subtype_field_definition (id) ON DELETE RESTRICT,
    key                 VARCHAR(50)  NOT NULL,
    label               VARCHAR(120) NOT NULL,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order          INTEGER      NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by_id       BIGINT       REFERENCES app_user (id),
    CONSTRAINT ck_subtype_option_key CHECK (key ~ '^[A-Z0-9][A-Z0-9_]{0,49}$'),
    UNIQUE (field_definition_id, key)
);

CREATE INDEX idx_subtype_field_option_order
    ON subtype_field_option (field_definition_id, active, sort_order, id);

CREATE TABLE ticket_field_value (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id           BIGINT NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    field_definition_id BIGINT NOT NULL REFERENCES subtype_field_definition (id) ON DELETE RESTRICT,
    text_value          TEXT,
    number_value        NUMERIC(19,4),
    date_value          DATE,
    boolean_value       BOOLEAN,
    selected_option_id  BIGINT REFERENCES subtype_field_option (id) ON DELETE RESTRICT,
    user_value_id       BIGINT REFERENCES app_user (id) ON DELETE RESTRICT,
    team_value_id       BIGINT REFERENCES developer_team (id) ON DELETE RESTRICT,
    reference_snapshot  VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by_id       BIGINT REFERENCES app_user (id),
    CONSTRAINT ck_ticket_field_one_scalar CHECK (
        num_nonnulls(text_value, number_value, date_value, boolean_value,
            selected_option_id, user_value_id, team_value_id) <= 1
    ),
    UNIQUE (ticket_id, field_definition_id)
);

CREATE INDEX idx_ticket_field_value_ticket ON ticket_field_value (ticket_id);
CREATE INDEX idx_ticket_field_value_definition ON ticket_field_value (field_definition_id);
CREATE INDEX idx_ticket_field_value_user ON ticket_field_value (user_value_id)
    WHERE user_value_id IS NOT NULL;

CREATE TABLE ticket_field_value_option (
    field_value_id BIGINT NOT NULL REFERENCES ticket_field_value (id) ON DELETE CASCADE,
    option_id      BIGINT NOT NULL REFERENCES subtype_field_option (id) ON DELETE RESTRICT,
    PRIMARY KEY (field_value_id, option_id)
);

CREATE INDEX idx_ticket_field_value_option_option
    ON ticket_field_value_option (option_id);

-- Source migration: V17__create_routing_and_decisions.sql
-- V17: deterministic subtype routing and append-only protected decisions.

CREATE TABLE subtype_routing_rule (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subtype_id            BIGINT      NOT NULL REFERENCES ticket_subtype (id) ON DELETE RESTRICT,
    organization_id       BIGINT      REFERENCES organization (id) ON DELETE RESTRICT,
    team_id               BIGINT      NOT NULL REFERENCES developer_team (id) ON DELETE RESTRICT,
    primary_developer_id  BIGINT      REFERENCES app_user (id) ON DELETE RESTRICT,
    fallback_developer_id BIGINT      REFERENCES app_user (id) ON DELETE RESTRICT,
    approver_id           BIGINT      REFERENCES app_user (id) ON DELETE RESTRICT,
    active                BOOLEAN     NOT NULL DEFAULT TRUE,
    version               BIGINT      NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by_id         BIGINT      REFERENCES app_user (id)
);

CREATE UNIQUE INDEX ux_subtype_routing_global_active
    ON subtype_routing_rule (subtype_id)
    WHERE organization_id IS NULL AND active;
CREATE UNIQUE INDEX ux_subtype_routing_org_active
    ON subtype_routing_rule (subtype_id, organization_id)
    WHERE organization_id IS NOT NULL AND active;
CREATE INDEX idx_subtype_routing_org ON subtype_routing_rule (organization_id);
CREATE INDEX idx_subtype_routing_team ON subtype_routing_rule (team_id);

CREATE TABLE ticket_decision (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id               BIGINT      NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    kind                    VARCHAR(24) NOT NULL
        CHECK (kind IN ('WORKFLOW_APPROVAL', 'CLIENT_ACCEPTANCE')),
    decision                VARCHAR(10) NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    actor_id                BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    from_state_id           BIGINT      NOT NULL REFERENCES workflow_state (id) ON DELETE RESTRICT,
    to_state_id             BIGINT      NOT NULL REFERENCES workflow_state (id) ON DELETE RESTRICT,
    reason                  VARCHAR(2000),
    observed_ticket_version BIGINT      NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_ticket_decision_reject_reason CHECK (
        decision <> 'REJECTED' OR length(btrim(reason)) >= 2
    )
);

CREATE INDEX idx_ticket_decision_ticket_created
    ON ticket_decision (ticket_id, created_at, id);
CREATE INDEX idx_ticket_decision_actor ON ticket_decision (actor_id);


-- Source migration: V18__add_ticket_workflow_context.sql
-- V18: nullable workflow context keeps every pre-feature ticket valid.

ALTER TABLE ticket
    ADD COLUMN subtype_id BIGINT REFERENCES ticket_subtype (id) ON DELETE RESTRICT,
    ADD COLUMN parent_ticket_id BIGINT REFERENCES ticket (id) ON DELETE RESTRICT,
    ADD COLUMN routing_rule_id BIGINT REFERENCES subtype_routing_rule (id) ON DELETE RESTRICT,
    ADD COLUMN resolved_approver_id BIGINT REFERENCES app_user (id) ON DELETE RESTRICT,
    ADD COLUMN client_acceptance_approver_id BIGINT REFERENCES app_user (id) ON DELETE RESTRICT,
    ADD COLUMN target_user_id BIGINT REFERENCES app_user (id) ON DELETE RESTRICT,
    ADD COLUMN target_user_display_snapshot VARCHAR(255),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_ticket_not_own_parent CHECK (parent_ticket_id IS NULL OR parent_ticket_id <> id),
    ADD CONSTRAINT ck_ticket_target_snapshot CHECK (
        target_user_id IS NULL OR length(btrim(target_user_display_snapshot)) > 0
    );

CREATE INDEX idx_ticket_subtype ON ticket (subtype_id);
CREATE INDEX idx_ticket_parent ON ticket (parent_ticket_id);
CREATE INDEX idx_ticket_org_parent ON ticket (organization_id, parent_ticket_id);
CREATE INDEX idx_ticket_routing_rule ON ticket (routing_rule_id);
CREATE INDEX idx_ticket_resolved_approver ON ticket (resolved_approver_id);
CREATE INDEX idx_ticket_client_acceptance_approver
    ON ticket (client_acceptance_approver_id);
CREATE INDEX idx_ticket_target_user ON ticket (target_user_id);


-- Final capability value for the legacy Defect template.
UPDATE ticket_type SET capability = 'DEFECT_SLA' WHERE key = 'DEFECT';

CREATE INDEX idx_ticket_type_org_active_order
    ON ticket_type (organization_id, active, sort_order, id);
CREATE INDEX idx_ticket_type_capability ON ticket_type (capability);


-- Approved service-request workflow templates.  These are templates so the
-- existing clone_org_templates function creates organization-owned copies.

INSERT INTO workflow(name, organization_id)
SELECT x.name, NULL FROM (VALUES ('TASI Workflow'),('USR Workflow'),('REQ Workflow')) x(name)
WHERE NOT EXISTS (SELECT 1 FROM workflow w WHERE w.name=x.name AND w.organization_id IS NULL);

INSERT INTO workflow_state(workflow_id,key,is_initial,is_terminal,sort_order)
SELECT w.id,s.key,s.initial_state,s.terminal_state,s.sort_order
FROM workflow w JOIN (VALUES
 ('TASI Workflow','NEW',true,false,10),('TASI Workflow','ANALYSIS',false,false,20),('TASI Workflow','PENDING_APPROVAL',false,false,30),('TASI Workflow','IMPLEMENTATION',false,false,40),('TASI Workflow','CLOSED',false,true,50),
 ('USR Workflow','NEW',true,false,10),('USR Workflow','ANALYSIS',false,false,20),('USR Workflow','PENDING_APPROVAL',false,false,30),('USR Workflow','IMPLEMENTATION',false,false,40),('USR Workflow','CLOSED',false,true,50),
 ('REQ Workflow','SUBMITTED',true,false,10),('REQ Workflow','ANALYSIS',false,false,20),('REQ Workflow','CLIENT_ACCEPTANCE',false,false,30),('REQ Workflow','DEPLOYMENT',false,false,40),('REQ Workflow','CLOSED',false,true,50)
) s(workflow_name,key,initial_state,terminal_state,sort_order) ON s.workflow_name=w.name
WHERE NOT EXISTS (SELECT 1 FROM workflow_state e WHERE e.workflow_id=w.id AND e.key=s.key);

INSERT INTO workflow_transition(workflow_id,from_state_id,to_state_id,required_permission_id,required_party,responsibility_after,operation_kind)
SELECT w.id,fs.id,ts.id,p.id,t.required_party,t.responsibility_after,t.operation_kind
FROM (VALUES
 ('TASI Workflow','NEW','ANALYSIS','TICKET_TRANSITION','TICKETFLOW1',NULL,'STANDARD'),('TASI Workflow','ANALYSIS','NEW','TICKET_TRANSITION','TICKETFLOW1',NULL,'CORRECTION_RETURN'),('TASI Workflow','ANALYSIS','PENDING_APPROVAL','TICKET_TRANSITION','TICKETFLOW1',NULL,'STANDARD'),('TASI Workflow','PENDING_APPROVAL','IMPLEMENTATION','TICKET_TRANSITION','TICKETFLOW1',NULL,'WORKFLOW_APPROVE'),('TASI Workflow','PENDING_APPROVAL','ANALYSIS','TICKET_TRANSITION','TICKETFLOW1',NULL,'WORKFLOW_REJECT'),('TASI Workflow','IMPLEMENTATION','CLOSED','TICKET_TRANSITION','TICKETFLOW1',NULL,'STANDARD'),
 ('USR Workflow','NEW','ANALYSIS','TICKET_TRANSITION','TICKETFLOW1',NULL,'STANDARD'),('USR Workflow','ANALYSIS','NEW','TICKET_TRANSITION','TICKETFLOW1',NULL,'CORRECTION_RETURN'),('USR Workflow','ANALYSIS','PENDING_APPROVAL','TICKET_TRANSITION','TICKETFLOW1',NULL,'STANDARD'),('USR Workflow','PENDING_APPROVAL','IMPLEMENTATION','TICKET_TRANSITION','TICKETFLOW1',NULL,'WORKFLOW_APPROVE'),('USR Workflow','PENDING_APPROVAL','ANALYSIS','TICKET_TRANSITION','TICKETFLOW1',NULL,'WORKFLOW_REJECT'),('USR Workflow','IMPLEMENTATION','CLOSED','TICKET_TRANSITION','TICKETFLOW1',NULL,'STANDARD'),
 ('REQ Workflow','SUBMITTED','ANALYSIS','TICKET_TRANSITION','TICKETFLOW1',NULL,'STANDARD'),('REQ Workflow','ANALYSIS','SUBMITTED','TICKET_TRANSITION','TICKETFLOW1',NULL,'CORRECTION_RETURN'),('REQ Workflow','ANALYSIS','CLIENT_ACCEPTANCE','TICKET_TRANSITION','TICKETFLOW1','CLIENT','STANDARD'),('REQ Workflow','CLIENT_ACCEPTANCE','DEPLOYMENT','TICKET_TRANSITION','CLIENT','TICKETFLOW1','CLIENT_ACCEPT'),('REQ Workflow','CLIENT_ACCEPTANCE','ANALYSIS','TICKET_TRANSITION','CLIENT','TICKETFLOW1','CLIENT_REJECT'),('REQ Workflow','DEPLOYMENT','ANALYSIS','TICKET_TRANSITION','TICKETFLOW1',NULL,'CORRECTION_RETURN'),('REQ Workflow','DEPLOYMENT','CLOSED','TICKET_TRANSITION','TICKETFLOW1',NULL,'STANDARD')
) t(workflow_name,from_key,to_key,permission_key,required_party,responsibility_after,operation_kind)
JOIN workflow w ON w.name=t.workflow_name AND w.organization_id IS NULL
JOIN workflow_state fs ON fs.workflow_id=w.id AND fs.key=t.from_key
JOIN workflow_state ts ON ts.workflow_id=w.id AND ts.key=t.to_key
JOIN permission p ON p.key=t.permission_key
WHERE NOT EXISTS (SELECT 1 FROM workflow_transition e WHERE e.workflow_id=w.id AND e.from_state_id=fs.id AND e.to_state_id=ts.id);

INSERT INTO ticket_type(key,name,workflow_id,organization_id,is_template,requires_proposal)
SELECT x.key,x.name,w.id,NULL,true,false FROM (VALUES
 ('TASI','Technical Service Action','TASI Workflow'),('USR','User Service Request','USR Workflow'),('DFCT','Defect','Defect Workflow'),('REQ','Request','REQ Workflow')
) x(key,name,workflow_name) JOIN workflow w ON w.name=x.workflow_name AND w.organization_id IS NULL
WHERE NOT EXISTS (SELECT 1 FROM ticket_type t WHERE t.organization_id IS NULL AND t.key=x.key);

UPDATE ticket_type SET capability='DEFECT_SLA'
WHERE organization_id IS NULL AND key='DFCT';

-- Starter subtype choices remain editable through the administration API.
INSERT INTO ticket_subtype(ticket_type_id,key,name,description,sort_order)
SELECT t.id,s.key,s.name,s.description,s.sort_order
FROM ticket_type t
JOIN (VALUES
 ('TASI','FIREWALL','Firewall','Firewall service action',10),
 ('TASI','NETWORK','Network','Network service action',20),
 ('TASI','APPLICATION','Application','Application service action',30),
 ('TASI','HARDWARE','Hardware','Hardware service action',40),
 ('USR','NEW','New user','Create a user',10),
 ('USR','MODIFY','Modify user','Change an existing user',20),
 ('USR','DELETE','Delete user','Remove an existing user',30)
) s(type_key,key,name,description,sort_order) ON s.type_key=t.key
WHERE t.organization_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM ticket_subtype e WHERE e.ticket_type_id=t.id AND e.key=s.key);

-- Source migration: V22_2__harden_template_workflow_cloning.sql
-- Harden template cloning against stale/partial data where duplicate template
-- workflows exist with organization_id NULL. PostgreSQL unique constraints do
-- not treat NULLs as equal, so duplicates can exist for template rows even
-- though organization-owned workflow names are unique.

CREATE OR REPLACE FUNCTION clone_org_templates(target_org_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    DROP TABLE IF EXISTS workflow_clone_map;
    DROP TABLE IF EXISTS state_clone_map;

    INSERT INTO role (name, party, organization_id, is_template)
    SELECT r.name, r.party, target_org_id, FALSE
    FROM role r
    WHERE r.is_template = TRUE
      AND r.party = 'CLIENT'
      AND NOT EXISTS (
          SELECT 1
          FROM role existing
          WHERE existing.organization_id = target_org_id
            AND existing.name = r.name
      );

    INSERT INTO role_permission (role_id, permission_id)
    SELECT cloned.id, rp.permission_id
    FROM role template
    JOIN role_permission rp ON rp.role_id = template.id
    JOIN role cloned
        ON cloned.organization_id = target_org_id
       AND cloned.name = template.name
       AND cloned.party = template.party
    WHERE template.is_template = TRUE
      AND template.party = 'CLIENT'
    ON CONFLICT DO NOTHING;

    CREATE TEMP TABLE workflow_clone_map (
        template_id BIGINT PRIMARY KEY,
        cloned_id   BIGINT NOT NULL
    ) ON COMMIT DROP;

    INSERT INTO workflow (name, organization_id)
    SELECT template.name, target_org_id
    FROM (
        SELECT DISTINCT ON (name) id, name
        FROM workflow
        WHERE organization_id IS NULL
        ORDER BY name, id
    ) template
    WHERE NOT EXISTS (
        SELECT 1
        FROM workflow existing
        WHERE existing.organization_id = target_org_id
          AND existing.name = template.name
    )
    ON CONFLICT DO NOTHING;

    INSERT INTO workflow_clone_map (template_id, cloned_id)
    SELECT template.id, cloned.id
    FROM (
        SELECT DISTINCT ON (name) id, name
        FROM workflow
        WHERE organization_id IS NULL
        ORDER BY name, id
    ) template
    JOIN workflow cloned
        ON cloned.organization_id = target_org_id
       AND cloned.name = template.name;

    CREATE TEMP TABLE state_clone_map (
        template_id BIGINT PRIMARY KEY,
        cloned_id   BIGINT NOT NULL
    ) ON COMMIT DROP;

    INSERT INTO workflow_state (workflow_id, key, is_initial, is_terminal, sort_order)
    SELECT map.cloned_id, ws.key, ws.is_initial, ws.is_terminal, ws.sort_order
    FROM workflow_state ws
    JOIN workflow_clone_map map ON map.template_id = ws.workflow_id
    LEFT JOIN workflow_state existing
        ON existing.workflow_id = map.cloned_id
       AND existing.key = ws.key
    WHERE existing.id IS NULL
    ON CONFLICT DO NOTHING;

    INSERT INTO state_clone_map (template_id, cloned_id)
    SELECT template.id, cloned.id
    FROM workflow_state template
    JOIN workflow_clone_map map ON map.template_id = template.workflow_id
    JOIN workflow_state cloned
        ON cloned.workflow_id = map.cloned_id
       AND cloned.key = template.key;

    INSERT INTO workflow_transition (
        workflow_id, from_state_id, to_state_id, required_permission_id, required_party, responsibility_after
    )
    SELECT map.cloned_id,
           from_map.cloned_id,
           to_map.cloned_id,
           wt.required_permission_id,
           wt.required_party,
           wt.responsibility_after
    FROM workflow_transition wt
    JOIN workflow_clone_map map ON map.template_id = wt.workflow_id
    JOIN state_clone_map from_map ON from_map.template_id = wt.from_state_id
    JOIN state_clone_map to_map ON to_map.template_id = wt.to_state_id
    LEFT JOIN workflow_transition existing
        ON existing.workflow_id = map.cloned_id
       AND existing.from_state_id = from_map.cloned_id
       AND existing.to_state_id = to_map.cloned_id
    WHERE existing.id IS NULL
    ON CONFLICT DO NOTHING;

    INSERT INTO ticket_type (key, name, workflow_id, organization_id, is_template, requires_proposal)
    SELECT tt.key,
           tt.name,
           map.cloned_id,
           target_org_id,
           FALSE,
           tt.requires_proposal
    FROM ticket_type tt
    JOIN workflow_clone_map map ON map.template_id = tt.workflow_id
    LEFT JOIN ticket_type existing
        ON existing.organization_id = target_org_id
       AND existing.key = tt.key
    WHERE tt.organization_id IS NULL
      AND existing.id IS NULL
    ON CONFLICT DO NOTHING;

    UPDATE ticket_type org_type
    SET active = template_type.active,
        sort_order = template_type.sort_order,
        capability = template_type.capability
    FROM ticket_type template_type
    WHERE template_type.organization_id IS NULL
      AND org_type.organization_id = target_org_id
      AND org_type.key = template_type.key;

    INSERT INTO ticket_subtype (ticket_type_id, key, name, description, active, sort_order)
    SELECT org_type.id, template_subtype.key, template_subtype.name,
           template_subtype.description, template_subtype.active, template_subtype.sort_order
    FROM ticket_type org_type
    JOIN ticket_type template_type
      ON template_type.organization_id IS NULL
     AND template_type.key = org_type.key
    JOIN ticket_subtype template_subtype
      ON template_subtype.ticket_type_id = template_type.id
    WHERE org_type.organization_id = target_org_id
      AND NOT EXISTS (
          SELECT 1 FROM ticket_subtype existing
          WHERE existing.ticket_type_id = org_type.id
            AND existing.key = template_subtype.key
      );

    UPDATE workflow_transition org_transition
    SET operation_kind = template_transition.operation_kind
    FROM workflow_transition template_transition
    JOIN workflow template_workflow ON template_workflow.id = template_transition.workflow_id
    JOIN workflow org_workflow
      ON org_workflow.organization_id = target_org_id
     AND org_workflow.name = template_workflow.name
    JOIN workflow_state template_from ON template_from.id = template_transition.from_state_id
    JOIN workflow_state template_to ON template_to.id = template_transition.to_state_id
    JOIN workflow_state org_from
      ON org_from.workflow_id = org_workflow.id
     AND org_from.key = template_from.key
    JOIN workflow_state org_to
      ON org_to.workflow_id = org_workflow.id
     AND org_to.key = template_to.key
    WHERE template_workflow.organization_id IS NULL
      AND org_transition.workflow_id = org_workflow.id
      AND org_transition.from_state_id = org_from.id
      AND org_transition.to_state_id = org_to.id;
END;
$$;

SELECT clone_org_templates(id) FROM organization;

-- Source migration: V25__seed_public_test_scenario.sql
-- Public test scenario for the internet-hosted app.
--
-- These accounts are intentionally separate from the demo profile. The shared
-- test password is communicated out-of-band; only the BCrypt hash is stored.

INSERT INTO organization (name)
SELECT 'Acme Field Services'
WHERE NOT EXISTS (SELECT 1 FROM organization WHERE name = 'Acme Field Services');

SELECT clone_org_templates(id)
FROM organization
WHERE name = 'Acme Field Services';

INSERT INTO app_user (email, password_hash, display_name, party, role_id, organization_id)
SELECT account.email,
       '$2b$10$FZ8WNA78vgbxN9URh86PlOK9qRkvCEwqE6iqs.8LP.oV/MKohpHJC',
       account.display_name,
       account.party,
       role.id,
       organization.id
FROM (VALUES
    ('test.admin@ticketflow1.app', 'TicketFlow1 Test Admin', 'TICKETFLOW1', 'Admin', NULL),
    ('test.manager@ticketflow1.app', 'Mila Operations Manager', 'TICKETFLOW1', 'TicketFlow1 Manager', NULL),
    ('test.developer@ticketflow1.app', 'Luka Platform Developer', 'TICKETFLOW1', 'TicketFlow1 User', NULL),
    ('owner@acme-test.app', 'Eva Business Owner', 'CLIENT', 'Client User', 'Acme Field Services'),
    ('approver@acme-test.app', 'Marko Client Approver', 'CLIENT', 'Client Approver', 'Acme Field Services'),
    ('requester@acme-test.app', 'Nina Store Requester', 'CLIENT', 'Client User', 'Acme Field Services')
) AS account(email, display_name, party, role_name, organization_name)
LEFT JOIN organization ON organization.name = account.organization_name
JOIN role ON role.name = account.role_name
 AND role.party = account.party
 AND role.organization_id IS NOT DISTINCT FROM organization.id
WHERE NOT EXISTS (SELECT 1 FROM app_user existing WHERE existing.email = account.email);

INSERT INTO app_user_role (user_id, role_id)
SELECT app_user.id, app_user.role_id
FROM app_user
WHERE app_user.email IN (
    'test.admin@ticketflow1.app',
    'test.manager@ticketflow1.app',
    'test.developer@ticketflow1.app',
    'owner@acme-test.app',
    'approver@acme-test.app',
    'requester@acme-test.app'
)
ON CONFLICT DO NOTHING;

INSERT INTO developer_team (name, description, leader_id, created_by_id)
SELECT team.name, team.description, leader.id, creator.id
FROM (VALUES
    ('Platform Operations', 'Handles infrastructure, firewall, network and platform incidents.', 'test.manager@ticketflow1.app'),
    ('Client Delivery', 'Handles client requests, deployments and acceptance follow-up.', 'test.developer@ticketflow1.app')
) AS team(name, description, leader_email)
JOIN app_user leader ON leader.email = team.leader_email
JOIN app_user creator ON creator.email = 'test.admin@ticketflow1.app'
WHERE NOT EXISTS (SELECT 1 FROM developer_team existing WHERE existing.name = team.name);

INSERT INTO developer_team_member (team_id, user_id)
SELECT developer_team.id, app_user.id
FROM developer_team
JOIN app_user ON app_user.email IN (
    'test.admin@ticketflow1.app',
    'test.manager@ticketflow1.app',
    'test.developer@ticketflow1.app'
)
WHERE developer_team.name IN ('Platform Operations', 'Client Delivery')
ON CONFLICT DO NOTHING;

INSERT INTO subtype_routing_rule (
    subtype_id, organization_id, team_id, primary_developer_id,
    fallback_developer_id, approver_id, active
)
SELECT subtype.id, organization.id, team.id, primary_dev.id, fallback_dev.id, approver.id, true
FROM (VALUES
    ('Acme Field Services', 'DFCT', 'NETWORK', 'Platform Operations', 'test.developer@ticketflow1.app', 'test.manager@ticketflow1.app', 'test.manager@ticketflow1.app'),
    ('Acme Field Services', 'REQ', 'APPLICATION', 'Client Delivery', 'test.developer@ticketflow1.app', 'test.manager@ticketflow1.app', 'test.manager@ticketflow1.app'),
    ('TicketFlow1 Internal', 'TASI', 'FIREWALL', 'Platform Operations', 'test.developer@ticketflow1.app', 'test.manager@ticketflow1.app', 'test.manager@ticketflow1.app'),
    ('TicketFlow1 Internal', 'USR', 'MODIFY', 'Client Delivery', 'test.developer@ticketflow1.app', 'test.manager@ticketflow1.app', 'test.manager@ticketflow1.app')
) AS route(organization_name, type_key, subtype_key, team_name, primary_email, fallback_email, approver_email)
JOIN organization ON organization.name = route.organization_name
JOIN ticket_type type ON type.organization_id = organization.id AND type.key = route.type_key
JOIN ticket_subtype subtype ON subtype.ticket_type_id = type.id AND subtype.key = route.subtype_key
JOIN developer_team team ON team.name = route.team_name
JOIN app_user primary_dev ON primary_dev.email = route.primary_email
JOIN app_user fallback_dev ON fallback_dev.email = route.fallback_email
JOIN app_user approver ON approver.email = route.approver_email
WHERE NOT EXISTS (
    SELECT 1 FROM subtype_routing_rule existing
    WHERE existing.subtype_id = subtype.id
      AND existing.organization_id IS NOT DISTINCT FROM organization.id
);

INSERT INTO ticket (
    ticket_key, ticket_type_id, current_state_id, priority, severity,
    title, description, organization_id, business_owner_id, ticket_lead_id,
    assigned_team, current_responsibility, subtype_id, target_user_id,
    target_user_display_snapshot, created_at, response_due_at,
    first_info_due_at, next_update_due_at
)
SELECT seed.ticket_key, type.id, state.id, seed.priority, seed.severity,
       seed.title, seed.description, organization.id, owner.id, lead.id,
       seed.assigned_team, seed.responsibility, subtype.id, target_user.id,
       seed.target_user_snapshot, seed.created_at, seed.response_due_at,
       seed.first_info_due_at, seed.next_update_due_at
FROM (VALUES
    ('TF-2000', 'Acme Field Services', 'owner@acme-test.app', 'DFCT', 'NETWORK', 'REPORTED',
     'CRITICAL', 'SEV_1', 'Warehouse scanner network outage',
     'Handheld scanners in warehouse B cannot reach the inventory API after the morning router restart.',
     'Platform Operations', 'TICKETFLOW1', NULL, NULL, now() - interval '70 minutes',
     now() - interval '10 minutes', now() + interval '50 minutes', now() + interval '20 minutes'),
    ('TF-2001', 'Acme Field Services', 'requester@acme-test.app', 'REQ', 'APPLICATION', 'ANALYSIS',
     'HIGH', NULL, 'Add delivery exception dashboard',
     'Operations needs a dashboard showing blocked deliveries grouped by depot and exception reason.',
     'Client Delivery', 'TICKETFLOW1', NULL, NULL, now() - interval '2 days',
     NULL, NULL, NULL),
    ('TF-2002', 'Acme Field Services', 'approver@acme-test.app', 'DFCT', 'NETWORK', 'ANALYSIS',
     'MEDIUM', 'SEV_3', 'Intermittent label printer disconnects',
     'Two depot label printers disconnect for a few minutes during peak packing windows.',
     'Platform Operations', 'TICKETFLOW1', NULL, NULL, now() - interval '8 hours',
     now() + interval '8 hours', now() + interval '20 hours', NULL),
    ('TF-2003', 'TicketFlow1 Internal', 'test.admin@ticketflow1.app', 'TASI', 'FIREWALL', 'ANALYSIS',
     'HIGH', NULL, 'Review VPN firewall rule for Acme',
     'Validate whether Acme warehouse VPN ranges should be added to the protected allow-list.',
     'Platform Operations', 'TICKETFLOW1', NULL, NULL, now() - interval '5 hours',
     NULL, NULL, NULL),
    ('TF-2004', 'TicketFlow1 Internal', 'test.manager@ticketflow1.app', 'USR', 'MODIFY', 'PENDING_APPROVAL',
     'MEDIUM', NULL, 'Modify Acme requester access',
     'Update requester profile so the user can see delivery exception requests but not admin workflow pages.',
     'Client Delivery', 'TICKETFLOW1', 'requester@acme-test.app', 'Nina Store Requester', now() - interval '1 day',
     NULL, NULL, NULL),
    ('TF-2005', 'Acme Field Services', 'owner@acme-test.app', 'REQ', 'APPLICATION', 'CLIENT_ACCEPTANCE',
     'LOW', NULL, 'Change report export filename',
     'Rename the daily export file so it includes organization key and report date.',
     'Client Delivery', 'CLIENT', NULL, NULL, now() - interval '4 days',
     NULL, NULL, NULL)
) AS seed(ticket_key, organization_name, owner_email, type_key, subtype_key, state_key,
          priority, severity, title, description, assigned_team, responsibility,
          target_user_email, target_user_snapshot, created_at, response_due_at,
          first_info_due_at, next_update_due_at)
JOIN organization ON organization.name = seed.organization_name
JOIN app_user owner ON owner.email = seed.owner_email
JOIN app_user lead ON lead.email = 'test.developer@ticketflow1.app'
JOIN ticket_type type ON type.organization_id = organization.id AND type.key = seed.type_key
JOIN workflow_state state ON state.workflow_id = type.workflow_id AND state.key = seed.state_key
LEFT JOIN ticket_subtype subtype ON subtype.ticket_type_id = type.id AND subtype.key = seed.subtype_key
LEFT JOIN app_user target_user ON target_user.email = seed.target_user_email
WHERE NOT EXISTS (SELECT 1 FROM ticket existing WHERE existing.ticket_key = seed.ticket_key);

INSERT INTO ticket_developer (ticket_id, user_id)
SELECT ticket.id, app_user.id
FROM ticket
JOIN app_user ON app_user.email IN ('test.developer@ticketflow1.app', 'test.manager@ticketflow1.app')
WHERE ticket.ticket_key IN ('TF-2000', 'TF-2001', 'TF-2002', 'TF-2003', 'TF-2004', 'TF-2005')
ON CONFLICT DO NOTHING;

INSERT INTO developer_team_ticket (team_id, ticket_id, sort_order)
SELECT team.id, ticket.id,
       row_number() OVER (PARTITION BY team.id ORDER BY ticket.ticket_key) - 1
FROM ticket
JOIN developer_team team ON team.name = ticket.assigned_team
WHERE ticket.ticket_key IN ('TF-2000', 'TF-2001', 'TF-2002', 'TF-2003', 'TF-2004', 'TF-2005')
  AND NOT EXISTS (
      SELECT 1 FROM developer_team_ticket existing
      WHERE existing.team_id = team.id AND existing.ticket_id = ticket.id
  );

INSERT INTO status_history (ticket_id, from_state_id, to_state_id, changed_by_id, created_at)
SELECT ticket.id, NULL, ticket.current_state_id, ticket.business_owner_id, ticket.created_at
FROM ticket
WHERE ticket.ticket_key IN ('TF-2000', 'TF-2001', 'TF-2002', 'TF-2003', 'TF-2004', 'TF-2005')
  AND NOT EXISTS (SELECT 1 FROM status_history existing WHERE existing.ticket_id = ticket.id);

INSERT INTO audit_log (ticket_id, actor_id, action, new_value, created_at)
SELECT ticket.id, ticket.business_owner_id, 'TICKET_CREATED', ticket.ticket_key, ticket.created_at
FROM ticket
WHERE ticket.ticket_key IN ('TF-2000', 'TF-2001', 'TF-2002', 'TF-2003', 'TF-2004', 'TF-2005')
  AND NOT EXISTS (
      SELECT 1 FROM audit_log existing
      WHERE existing.ticket_id = ticket.id AND existing.action = 'TICKET_CREATED'
  );

SELECT setval('ticket_key_seq', GREATEST((SELECT last_value FROM ticket_key_seq), 2005), true);

-- Source migration: V26__create_ticket_approval.sql
-- Feature 003: explicit pending approval lifecycle.
--
-- ticket_decision remains append-only evidence of the final choice. This table
-- represents the active approval assignment created when a ticket enters a
-- workflow-approval state, so authorization never has to infer pending work
-- from a nullable ticket column alone.

CREATE TABLE ticket_approval (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id            BIGINT       NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    pending_state_id     BIGINT       NOT NULL REFERENCES workflow_state (id) ON DELETE RESTRICT,
    assigned_approver_id BIGINT       REFERENCES app_user (id) ON DELETE RESTRICT,
    assigned_team_id     BIGINT       REFERENCES developer_team (id) ON DELETE RESTRICT,
    status               VARCHAR(12)  NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    decided_by_id        BIGINT       REFERENCES app_user (id) ON DELETE RESTRICT,
    decided_at           TIMESTAMPTZ,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_ticket_approval_assignment CHECK (
        assigned_approver_id IS NOT NULL OR assigned_team_id IS NOT NULL
    ),
    CONSTRAINT ck_ticket_approval_decision CHECK (
        (status = 'PENDING' AND decided_by_id IS NULL AND decided_at IS NULL)
        OR
        (status <> 'PENDING' AND decided_by_id IS NOT NULL AND decided_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX ux_ticket_approval_pending
    ON ticket_approval (ticket_id)
    WHERE status = 'PENDING';
CREATE INDEX idx_ticket_approval_assigned_approver
    ON ticket_approval (assigned_approver_id, status);
CREATE INDEX idx_ticket_approval_assigned_team
    ON ticket_approval (assigned_team_id, status);

-- Existing tickets may already be in an approval state. Resolve their current
-- active routing rule so the additive migration does not leave them
-- permanently undecidable.
INSERT INTO ticket_approval (
    ticket_id, pending_state_id, assigned_approver_id, assigned_team_id
)
SELECT ticket.id,
       ticket.current_state_id,
       COALESCE(ticket.resolved_approver_id, routing.approver_id),
       routing.team_id
FROM ticket
JOIN workflow_transition approve_edge
  ON approve_edge.workflow_id = (
      SELECT type.workflow_id FROM ticket_type type WHERE type.id = ticket.ticket_type_id
  )
 AND approve_edge.from_state_id = ticket.current_state_id
 AND approve_edge.operation_kind = 'WORKFLOW_APPROVE'
LEFT JOIN subtype_routing_rule routing
  ON routing.subtype_id = ticket.subtype_id
 AND routing.active
 AND routing.organization_id IS NOT DISTINCT FROM ticket.organization_id
WHERE COALESCE(ticket.resolved_approver_id, routing.approver_id) IS NOT NULL
   OR routing.team_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- Source migration: V27__add_approve_all_tickets_permission.sql
-- Feature 003 Phase 2: fixed, developer-owned global approval override.
--
-- The permission is granted to the seeded internal Admin role. Custom roles
-- may receive it through Role Administration; client roles never receive it by
-- default and the domain service additionally requires TICKETFLOW1 party.

INSERT INTO permission (key)
VALUES ('APPROVE_ALL_TICKETS')
ON CONFLICT (key) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM role
JOIN permission ON permission.key = 'APPROVE_ALL_TICKETS'
WHERE role.name = 'Admin'
  AND role.party = 'TICKETFLOW1'
  AND role.organization_id IS NULL
ON CONFLICT DO NOTHING;



-- Source migration: V30__create_user_organization_preferences.sql
CREATE TABLE user_organization_preference (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    organization_id         BIGINT REFERENCES organization (id) ON DELETE CASCADE,
    dashboard_widgets       JSONB NOT NULL,
    enabled_ticket_filters  JSONB NOT NULL,
    last_viewed_team_id     BIGINT REFERENCES developer_team (id) ON DELETE SET NULL,
    theme                   VARCHAR(12) NOT NULL DEFAULT 'SYSTEM'
                                CHECK (theme IN ('SYSTEM', 'LIGHT', 'DARK')),
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (jsonb_typeof(dashboard_widgets) = 'array'),
    CHECK (jsonb_typeof(enabled_ticket_filters) = 'array')
);

CREATE UNIQUE INDEX uq_user_preference_client_scope
    ON user_organization_preference (user_id, organization_id)
    WHERE organization_id IS NOT NULL;

CREATE UNIQUE INDEX uq_user_preference_internal_scope
    ON user_organization_preference (user_id)
    WHERE organization_id IS NULL;

CREATE INDEX idx_user_preference_organization
    ON user_organization_preference (organization_id);

-- Source migration: V32__create_subtype_field_role_grant.sql
CREATE TABLE subtype_field_role_grant (
    id BIGSERIAL PRIMARY KEY,
    field_id BIGINT NOT NULL REFERENCES subtype_field_definition(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    operation VARCHAR(10) NOT NULL CHECK (operation IN ('VIEW','EDIT','CREATE')),
    CONSTRAINT uq_subtype_field_role_operation UNIQUE(field_id, role_id, operation)
);
CREATE INDEX idx_field_role_grant_field ON subtype_field_role_grant(field_id);
CREATE INDEX idx_field_role_grant_role ON subtype_field_role_grant(role_id);

-- Final display names for the initial workflow states.
UPDATE workflow_state SET name = key WHERE name IS NULL;
CREATE UNIQUE INDEX ux_workflow_state_workflow_name ON workflow_state (workflow_id, name);

-- Source migration: V35__create_notifications.sql
CREATE TABLE notification (
    id BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT NOT NULL REFERENCES app_user(id),
    ticket_id BIGINT REFERENCES ticket(id),
    event_type VARCHAR(60) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    actor_id BIGINT REFERENCES app_user(id),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_recipient_created ON notification(recipient_id, created_at DESC);
CREATE INDEX idx_notification_recipient_unread ON notification(recipient_id) WHERE read_at IS NULL;

-- Source migration: V36__create_ticket_followers.sql
CREATE TABLE ticket_follower (
    ticket_id BIGINT NOT NULL REFERENCES ticket(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    muted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ticket_id, user_id)
);

-- Source migration: V40__create_login_rate_limit.sql
CREATE TABLE login_rate_limit (
    email_key VARCHAR(255) PRIMARY KEY,
    window_started_at TIMESTAMPTZ NOT NULL,
    failures INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
