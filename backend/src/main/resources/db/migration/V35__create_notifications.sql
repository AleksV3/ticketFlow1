CREATE TABLE notification (
    id BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT NOT NULL REFERENCES app_user(id),
    ticket_id BIGINT REFERENCES ticket(id),
    event_type VARCHAR(60) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_recipient_created ON notification(recipient_id, created_at DESC);
CREATE INDEX idx_notification_recipient_unread ON notification(recipient_id) WHERE read_at IS NULL;
