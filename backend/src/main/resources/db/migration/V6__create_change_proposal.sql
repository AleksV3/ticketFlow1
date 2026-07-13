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
