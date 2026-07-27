ALTER TABLE notification ADD COLUMN actor_id BIGINT REFERENCES app_user(id);
