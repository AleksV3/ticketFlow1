-- Harden template cloning against stale/partial data where duplicate template
-- workflows exist with organization_id NULL. PostgreSQL unique constraints do
-- not treat NULLs as equal, so duplicates can exist for template rows even
-- though organization-owned workflow names are unique.

CREATE OR REPLACE FUNCTION clone_org_templates(target_org_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    DROP TABLE IF EXISTS workflow_clone_map;
    DROP TABLE IF EXISTS state_clone_map;

    INSERT INTO role (name, party, organization_id, is_template)
    SELECT r.name, r.party, target_org_id, FALSE
    FROM role r
    WHERE r.is_template = TRUE
      AND r.party = 'CLIENT'
      AND NOT EXISTS (
          SELECT 1
          FROM role existing
          WHERE existing.organization_id = target_org_id
            AND existing.name = r.name
      );

    INSERT INTO role_permission (role_id, permission_id)
    SELECT cloned.id, rp.permission_id
    FROM role template
    JOIN role_permission rp ON rp.role_id = template.id
    JOIN role cloned
        ON cloned.organization_id = target_org_id
       AND cloned.name = template.name
       AND cloned.party = template.party
    WHERE template.is_template = TRUE
      AND template.party = 'CLIENT'
    ON CONFLICT DO NOTHING;

    CREATE TEMP TABLE workflow_clone_map (
        template_id BIGINT PRIMARY KEY,
        cloned_id   BIGINT NOT NULL
    ) ON COMMIT DROP;

    INSERT INTO workflow (name, organization_id)
    SELECT template.name, target_org_id
    FROM (
        SELECT DISTINCT ON (name) id, name
        FROM workflow
        WHERE organization_id IS NULL
        ORDER BY name, id
    ) template
    WHERE NOT EXISTS (
        SELECT 1
        FROM workflow existing
        WHERE existing.organization_id = target_org_id
          AND existing.name = template.name
    )
    ON CONFLICT DO NOTHING;

    INSERT INTO workflow_clone_map (template_id, cloned_id)
    SELECT template.id, cloned.id
    FROM (
        SELECT DISTINCT ON (name) id, name
        FROM workflow
        WHERE organization_id IS NULL
        ORDER BY name, id
    ) template
    JOIN workflow cloned
        ON cloned.organization_id = target_org_id
       AND cloned.name = template.name;

    CREATE TEMP TABLE state_clone_map (
        template_id BIGINT PRIMARY KEY,
        cloned_id   BIGINT NOT NULL
    ) ON COMMIT DROP;

    INSERT INTO workflow_state (workflow_id, key, is_initial, is_terminal, sort_order)
    SELECT map.cloned_id, ws.key, ws.is_initial, ws.is_terminal, ws.sort_order
    FROM workflow_state ws
    JOIN workflow_clone_map map ON map.template_id = ws.workflow_id
    LEFT JOIN workflow_state existing
        ON existing.workflow_id = map.cloned_id
       AND existing.key = ws.key
    WHERE existing.id IS NULL
    ON CONFLICT DO NOTHING;

    INSERT INTO state_clone_map (template_id, cloned_id)
    SELECT template.id, cloned.id
    FROM workflow_state template
    JOIN workflow_clone_map map ON map.template_id = template.workflow_id
    JOIN workflow_state cloned
        ON cloned.workflow_id = map.cloned_id
       AND cloned.key = template.key;

    INSERT INTO workflow_transition (
        workflow_id, from_state_id, to_state_id, required_permission_id, required_party, responsibility_after
    )
    SELECT map.cloned_id,
           from_map.cloned_id,
           to_map.cloned_id,
           wt.required_permission_id,
           wt.required_party,
           wt.responsibility_after
    FROM workflow_transition wt
    JOIN workflow_clone_map map ON map.template_id = wt.workflow_id
    JOIN state_clone_map from_map ON from_map.template_id = wt.from_state_id
    JOIN state_clone_map to_map ON to_map.template_id = wt.to_state_id
    LEFT JOIN workflow_transition existing
        ON existing.workflow_id = map.cloned_id
       AND existing.from_state_id = from_map.cloned_id
       AND existing.to_state_id = to_map.cloned_id
    WHERE existing.id IS NULL
    ON CONFLICT DO NOTHING;

    INSERT INTO ticket_type (key, name, workflow_id, organization_id, is_template, requires_proposal)
    SELECT tt.key,
           tt.name,
           map.cloned_id,
           target_org_id,
           FALSE,
           tt.requires_proposal
    FROM ticket_type tt
    JOIN workflow_clone_map map ON map.template_id = tt.workflow_id
    LEFT JOIN ticket_type existing
        ON existing.organization_id = target_org_id
       AND existing.key = tt.key
    WHERE tt.organization_id IS NULL
      AND existing.id IS NULL
    ON CONFLICT DO NOTHING;
END;
$$;

SELECT clone_org_templates(id) FROM organization;
