-- Accounts created by an administrator begin with an administrator-provided
-- one-time password. Existing accounts keep their current login experience.
ALTER TABLE app_user
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
