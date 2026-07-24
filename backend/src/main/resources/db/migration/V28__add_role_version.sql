-- Optimistic concurrency for authoritative role permission-set replacement.
ALTER TABLE role
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

