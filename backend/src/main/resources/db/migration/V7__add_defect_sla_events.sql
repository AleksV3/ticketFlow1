-- V7: completion events used to evaluate Defect SLA obligations at read time.
ALTER TABLE ticket
    ADD COLUMN responded_at TIMESTAMPTZ,
    ADD COLUMN first_info_at TIMESTAMPTZ;

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
