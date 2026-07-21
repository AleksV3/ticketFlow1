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

