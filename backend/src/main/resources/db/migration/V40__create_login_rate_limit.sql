CREATE TABLE login_rate_limit (
    email_key VARCHAR(255) PRIMARY KEY,
    window_started_at TIMESTAMPTZ NOT NULL,
    failures INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
