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

ALTER TABLE audit_log DROP CONSTRAINT IF EXISTS audit_log_action_check;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_action_check CHECK (action IN (
    'TICKET_CREATED','STATUS_CHANGED','ASSIGNEE_CHANGED','COMMENT_ADDED','PROPOSAL_CREATED',
    'PROPOSAL_APPROVED','PROPOSAL_REJECTED','SEVERITY_CHANGED','PRIORITY_CHANGED',
    'ATTACHMENT_ADDED','DYNAMIC_FIELDS_CAPTURED','CORRECTION_RETURN',
    'WORKFLOW_APPROVED','WORKFLOW_REJECTED','TICKET_UPDATED','CONFIG_CHANGED'
));
