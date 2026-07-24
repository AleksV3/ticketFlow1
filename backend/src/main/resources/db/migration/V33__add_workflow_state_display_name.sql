ALTER TABLE workflow_state ADD COLUMN name VARCHAR(100);
UPDATE workflow_state SET name = key WHERE name IS NULL;
CREATE UNIQUE INDEX ux_workflow_state_workflow_name ON workflow_state (workflow_id, name);
