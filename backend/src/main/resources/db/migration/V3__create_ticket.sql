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
    next_update_due_at     TIMESTAMPTZ
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
