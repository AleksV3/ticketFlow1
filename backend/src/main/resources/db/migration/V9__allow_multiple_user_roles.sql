CREATE TABLE app_user_role (
    user_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES role (id),
    PRIMARY KEY (user_id, role_id)
);

INSERT INTO app_user_role (user_id, role_id)
SELECT id, role_id FROM app_user;
