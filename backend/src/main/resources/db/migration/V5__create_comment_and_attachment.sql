-- Phase 4 communication records. Attachments are metadata references only;
-- no file bytes are stored by the MVP.

CREATE TABLE comment (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id     BIGINT       NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    author_id     BIGINT       NOT NULL REFERENCES app_user (id),
    body          TEXT         NOT NULL CHECK (length(btrim(body)) BETWEEN 1 AND 10000),
    visibility    VARCHAR(10)  NOT NULL CHECK (visibility IN ('INTERNAL', 'PUBLIC')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by_id BIGINT       REFERENCES app_user (id)
);

CREATE INDEX idx_comment_ticket_created_at ON comment (ticket_id, created_at);

CREATE TABLE attachment (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id     BIGINT        NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    uploaded_by_id BIGINT       NOT NULL REFERENCES app_user (id),
    file_name     VARCHAR(255)  NOT NULL CHECK (length(btrim(file_name)) BETWEEN 1 AND 255),
    content_type  VARCHAR(100)  NOT NULL CHECK (
        length(btrim(content_type)) BETWEEN 3 AND 100
        AND position('/' IN content_type) > 1
    ),
    size_bytes    BIGINT        NOT NULL CHECK (size_bytes >= 0),
    storage_path  VARCHAR(500),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by_id BIGINT        REFERENCES app_user (id)
);

CREATE INDEX idx_attachment_ticket_created_at ON attachment (ticket_id, created_at);
