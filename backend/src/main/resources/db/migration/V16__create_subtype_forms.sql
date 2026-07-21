-- V16: bounded configurable subtype forms. Definitions are data in a fixed schema;
-- no runtime DDL, scripts, expressions, HTML, SQL, or custom field kinds.

CREATE TABLE ticket_subtype (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_type_id BIGINT       NOT NULL REFERENCES ticket_type (id) ON DELETE RESTRICT,
    key            VARCHAR(50)  NOT NULL,
    name           VARCHAR(120) NOT NULL,
    description    VARCHAR(1000),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order     INTEGER      NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by_id  BIGINT       REFERENCES app_user (id),
    CONSTRAINT ck_ticket_subtype_key CHECK (key ~ '^[A-Z][A-Z0-9_]{1,49}$'),
    UNIQUE (ticket_type_id, key)
);

CREATE INDEX idx_ticket_subtype_type_order
    ON ticket_subtype (ticket_type_id, active, sort_order, id);

CREATE TABLE subtype_field_definition (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subtype_id     BIGINT       NOT NULL REFERENCES ticket_subtype (id) ON DELETE RESTRICT,
    key            VARCHAR(50)  NOT NULL,
    label          VARCHAR(120) NOT NULL,
    help_text      VARCHAR(1000),
    field_kind     VARCHAR(20)  NOT NULL CHECK (field_kind IN (
        'SHORT_TEXT', 'LONG_TEXT', 'INTEGER', 'DECIMAL', 'DATE', 'BOOLEAN',
        'SINGLE_SELECT', 'MULTI_SELECT', 'USER_REFERENCE', 'TEAM_REFERENCE'
    )),
    required       BOOLEAN      NOT NULL DEFAULT FALSE,
    visibility     VARCHAR(10)  NOT NULL DEFAULT 'INTERNAL'
        CHECK (visibility IN ('PUBLIC', 'INTERNAL')),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order     INTEGER      NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    min_length     INTEGER,
    max_length     INTEGER,
    min_number     NUMERIC(19,4),
    max_number     NUMERIC(19,4),
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by_id  BIGINT       REFERENCES app_user (id),
    CONSTRAINT ck_subtype_field_key CHECK (key ~ '^[a-z][a-z0-9_]{1,49}$'),
    CONSTRAINT ck_subtype_field_lengths CHECK (
        (min_length IS NULL OR min_length >= 0) AND
        (max_length IS NULL OR max_length > 0) AND
        (min_length IS NULL OR max_length IS NULL OR min_length <= max_length)
    ),
    CONSTRAINT ck_subtype_field_numbers CHECK (
        min_number IS NULL OR max_number IS NULL OR min_number <= max_number
    ),
    UNIQUE (subtype_id, key)
);

CREATE INDEX idx_subtype_field_definition_order
    ON subtype_field_definition (subtype_id, active, sort_order, id);

CREATE TABLE subtype_field_option (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    field_definition_id BIGINT       NOT NULL REFERENCES subtype_field_definition (id) ON DELETE RESTRICT,
    key                 VARCHAR(50)  NOT NULL,
    label               VARCHAR(120) NOT NULL,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order          INTEGER      NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by_id       BIGINT       REFERENCES app_user (id),
    CONSTRAINT ck_subtype_option_key CHECK (key ~ '^[A-Z0-9][A-Z0-9_]{0,49}$'),
    UNIQUE (field_definition_id, key)
);

CREATE INDEX idx_subtype_field_option_order
    ON subtype_field_option (field_definition_id, active, sort_order, id);

CREATE TABLE ticket_field_value (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id           BIGINT NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    field_definition_id BIGINT NOT NULL REFERENCES subtype_field_definition (id) ON DELETE RESTRICT,
    text_value          TEXT,
    number_value        NUMERIC(19,4),
    date_value          DATE,
    boolean_value       BOOLEAN,
    selected_option_id  BIGINT REFERENCES subtype_field_option (id) ON DELETE RESTRICT,
    user_value_id       BIGINT REFERENCES app_user (id) ON DELETE RESTRICT,
    team_value_id       BIGINT REFERENCES developer_team (id) ON DELETE RESTRICT,
    reference_snapshot  VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by_id       BIGINT REFERENCES app_user (id),
    CONSTRAINT ck_ticket_field_one_scalar CHECK (
        num_nonnulls(text_value, number_value, date_value, boolean_value,
            selected_option_id, user_value_id, team_value_id) <= 1
    ),
    UNIQUE (ticket_id, field_definition_id)
);

CREATE INDEX idx_ticket_field_value_ticket ON ticket_field_value (ticket_id);
CREATE INDEX idx_ticket_field_value_definition ON ticket_field_value (field_definition_id);
CREATE INDEX idx_ticket_field_value_user ON ticket_field_value (user_value_id)
    WHERE user_value_id IS NOT NULL;

CREATE TABLE ticket_field_value_option (
    field_value_id BIGINT NOT NULL REFERENCES ticket_field_value (id) ON DELETE CASCADE,
    option_id      BIGINT NOT NULL REFERENCES subtype_field_option (id) ON DELETE RESTRICT,
    PRIMARY KEY (field_value_id, option_id)
);

CREATE INDEX idx_ticket_field_value_option_option
    ON ticket_field_value_option (option_id);
