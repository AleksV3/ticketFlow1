CREATE TABLE developer_team (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    leader_id BIGINT NOT NULL REFERENCES app_user (id),
    created_by_id BIGINT NOT NULL REFERENCES app_user (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE developer_team_member (
    team_id BIGINT NOT NULL REFERENCES developer_team (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user (id),
    PRIMARY KEY (team_id, user_id)
);

CREATE TABLE developer_team_ticket (
    team_id BIGINT NOT NULL REFERENCES developer_team (id) ON DELETE CASCADE,
    ticket_id BIGINT NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    PRIMARY KEY (team_id, ticket_id)
);
