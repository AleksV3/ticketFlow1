-- V19: type availability is data; capability remains a fixed code-owned set.

ALTER TABLE ticket_type
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    ADD COLUMN capability VARCHAR(20) NOT NULL DEFAULT 'STANDARD'
        CHECK (capability IN ('STANDARD', 'DEFECT_SLA')),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE ticket_type SET capability = 'DEFECT_SLA' WHERE key = 'DEFECT';

CREATE INDEX idx_ticket_type_org_active_order
    ON ticket_type (organization_id, active, sort_order, id);
CREATE INDEX idx_ticket_type_capability ON ticket_type (capability);

