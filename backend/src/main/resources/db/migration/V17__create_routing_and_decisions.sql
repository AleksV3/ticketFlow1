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

