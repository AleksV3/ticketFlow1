-- Backfill service-request subtype choices and preserve the workflow metadata
-- for organizations that already ran V20 before subtype cloning was added.

UPDATE ticket_type
SET capability = 'DEFECT_SLA'
WHERE organization_id IS NULL AND key = 'DFCT';

INSERT INTO ticket_subtype(ticket_type_id, key, name, description, sort_order)
SELECT t.id, s.key, s.name, s.description, s.sort_order
FROM ticket_type t
JOIN (VALUES
    ('TASI','FIREWALL','Firewall','Firewall service action',10),
    ('TASI','NETWORK','Network','Network service action',20),
    ('TASI','APPLICATION','Application','Application service action',30),
    ('TASI','HARDWARE','Hardware','Hardware service action',40),
    ('USR','NEW','New user','Create a user',10),
    ('USR','MODIFY','Modify user','Change an existing user',20),
    ('USR','DELETE','Delete user','Remove an existing user',30)
) s(type_key, key, name, description, sort_order) ON s.type_key = t.key
WHERE t.organization_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM ticket_subtype existing
      WHERE existing.ticket_type_id = t.id AND existing.key = s.key
  );

ALTER FUNCTION clone_org_templates(BIGINT) RENAME TO clone_org_templates_base;

CREATE OR REPLACE FUNCTION clone_org_templates(target_org_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM clone_org_templates_base(target_org_id);

    INSERT INTO ticket_subtype(ticket_type_id, key, name, description, sort_order)
    SELECT org_type.id, template_subtype.key, template_subtype.name,
           template_subtype.description, template_subtype.sort_order
    FROM ticket_type org_type
    JOIN ticket_type template_type
      ON template_type.organization_id IS NULL
     AND template_type.key = org_type.key
    JOIN ticket_subtype template_subtype
      ON template_subtype.ticket_type_id = template_type.id
    WHERE org_type.organization_id = target_org_id
      AND NOT EXISTS (
          SELECT 1 FROM ticket_subtype existing
          WHERE existing.ticket_type_id = org_type.id
            AND existing.key = template_subtype.key
      );

    UPDATE ticket_type org_type
    SET capability = template_type.capability
    FROM ticket_type template_type
    WHERE template_type.organization_id IS NULL
      AND org_type.organization_id = target_org_id
      AND org_type.key = template_type.key;

    UPDATE workflow_transition org_transition
    SET operation_kind = template_transition.operation_kind
    FROM workflow_transition template_transition
    JOIN workflow template_workflow ON template_workflow.id = template_transition.workflow_id
    JOIN workflow org_workflow
      ON org_workflow.organization_id = target_org_id
     AND org_workflow.name = template_workflow.name
    JOIN workflow_state template_from ON template_from.id = template_transition.from_state_id
    JOIN workflow_state template_to ON template_to.id = template_transition.to_state_id
    JOIN workflow_state org_from
      ON org_from.workflow_id = org_workflow.id
     AND org_from.key = template_from.key
    JOIN workflow_state org_to
      ON org_to.workflow_id = org_workflow.id
     AND org_to.key = template_to.key
    WHERE template_workflow.organization_id IS NULL
      AND org_transition.workflow_id = org_workflow.id
      AND org_transition.from_state_id = org_from.id
      AND org_transition.to_state_id = org_to.id;
END;
$$;

SELECT clone_org_templates(id) FROM organization;
