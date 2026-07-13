ALTER TABLE workflow_transition
    ADD COLUMN operation_kind VARCHAR(20) NOT NULL DEFAULT 'STANDARD'
    CHECK (operation_kind IN ('STANDARD', 'PROPOSAL_CREATE', 'PROPOSAL_APPROVE', 'PROPOSAL_REJECT'));

UPDATE workflow_transition wt
SET operation_kind = CASE
    WHEN fs.key = 'ANALYSIS' AND ts.key = 'PROPOSAL' THEN 'PROPOSAL_CREATE'
    WHEN fs.key = 'PROPOSAL' AND ts.key = 'PROPOSAL_APPROVED' THEN 'PROPOSAL_APPROVE'
    WHEN fs.key = 'PROPOSAL' AND ts.key = 'PROPOSAL_REJECTED' THEN 'PROPOSAL_REJECT'
    ELSE 'STANDARD'
END
FROM workflow_state fs, workflow_state ts
WHERE fs.id = wt.from_state_id AND ts.id = wt.to_state_id;

-- clone_org_templates copies transitions without naming operation_kind. This
-- trigger preserves protection for future organization clones as well.
CREATE FUNCTION assign_proposal_operation_kind() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE from_key VARCHAR(40); to_key VARCHAR(40);
BEGIN
    SELECT key INTO from_key FROM workflow_state WHERE id = NEW.from_state_id;
    SELECT key INTO to_key FROM workflow_state WHERE id = NEW.to_state_id;
    NEW.operation_kind := CASE
        WHEN from_key = 'ANALYSIS' AND to_key = 'PROPOSAL' THEN 'PROPOSAL_CREATE'
        WHEN from_key = 'PROPOSAL' AND to_key = 'PROPOSAL_APPROVED' THEN 'PROPOSAL_APPROVE'
        WHEN from_key = 'PROPOSAL' AND to_key = 'PROPOSAL_REJECTED' THEN 'PROPOSAL_REJECT'
        ELSE COALESCE(NEW.operation_kind, 'STANDARD')
    END;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_workflow_transition_operation_kind
BEFORE INSERT ON workflow_transition
FOR EACH ROW EXECUTE FUNCTION assign_proposal_operation_kind();
