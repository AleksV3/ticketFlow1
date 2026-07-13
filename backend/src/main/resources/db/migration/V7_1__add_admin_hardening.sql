ALTER TABLE workflow ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

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
