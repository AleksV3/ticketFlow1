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

