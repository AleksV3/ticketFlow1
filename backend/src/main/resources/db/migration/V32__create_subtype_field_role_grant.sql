CREATE TABLE subtype_field_role_grant (
    id BIGSERIAL PRIMARY KEY,
    field_id BIGINT NOT NULL REFERENCES subtype_field_definition(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    operation VARCHAR(10) NOT NULL CHECK (operation IN ('VIEW','EDIT','CREATE')),
    CONSTRAINT uq_subtype_field_role_operation UNIQUE(field_id, role_id, operation)
);
CREATE INDEX idx_field_role_grant_field ON subtype_field_role_grant(field_id);
CREATE INDEX idx_field_role_grant_role ON subtype_field_role_grant(role_id);
