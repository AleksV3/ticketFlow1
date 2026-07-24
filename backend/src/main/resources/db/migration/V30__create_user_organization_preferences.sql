CREATE TABLE user_organization_preference (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    organization_id         BIGINT REFERENCES organization (id) ON DELETE CASCADE,
    dashboard_widgets       JSONB NOT NULL,
    enabled_ticket_filters  JSONB NOT NULL,
    last_viewed_team_id     BIGINT REFERENCES developer_team (id) ON DELETE SET NULL,
    theme                   VARCHAR(12) NOT NULL DEFAULT 'SYSTEM'
                                CHECK (theme IN ('SYSTEM', 'LIGHT', 'DARK')),
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (jsonb_typeof(dashboard_widgets) = 'array'),
    CHECK (jsonb_typeof(enabled_ticket_filters) = 'array')
);

CREATE UNIQUE INDEX uq_user_preference_client_scope
    ON user_organization_preference (user_id, organization_id)
    WHERE organization_id IS NOT NULL;

CREATE UNIQUE INDEX uq_user_preference_internal_scope
    ON user_organization_preference (user_id)
    WHERE organization_id IS NULL;

CREATE INDEX idx_user_preference_organization
    ON user_organization_preference (organization_id);
