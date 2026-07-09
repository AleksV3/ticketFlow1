-- V2: workflow/type configuration model. Global template workflows + ticket
-- types are seeded here, then cloned per client Organization so ticket
-- creation can resolve organization-owned definitions immediately.

CREATE TABLE workflow (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    organization_id BIGINT REFERENCES organization (id),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by_id   BIGINT REFERENCES app_user (id),
    UNIQUE (organization_id, name)
);

CREATE TABLE workflow_state (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workflow_id   BIGINT       NOT NULL REFERENCES workflow (id) ON DELETE CASCADE,
    key           VARCHAR(40)  NOT NULL,
    is_initial    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_terminal   BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order    INTEGER      NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by_id BIGINT       REFERENCES app_user (id),
    UNIQUE (workflow_id, key)
);

CREATE UNIQUE INDEX ux_workflow_state_one_initial
    ON workflow_state (workflow_id)
    WHERE is_initial;

CREATE TABLE workflow_transition (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workflow_id            BIGINT       NOT NULL REFERENCES workflow (id) ON DELETE CASCADE,
    from_state_id          BIGINT       NOT NULL REFERENCES workflow_state (id) ON DELETE CASCADE,
    to_state_id            BIGINT       NOT NULL REFERENCES workflow_state (id) ON DELETE CASCADE,
    required_permission_id BIGINT       NOT NULL REFERENCES permission (id),
    required_party         VARCHAR(12)  CHECK (required_party IN ('CLIENT', 'TICKETFLOW1')),
    responsibility_after   VARCHAR(12)  CHECK (responsibility_after IN ('CLIENT', 'TICKETFLOW1')),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by_id          BIGINT       REFERENCES app_user (id),
    UNIQUE (workflow_id, from_state_id, to_state_id)
);

CREATE TABLE ticket_type (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    key               VARCHAR(40)  NOT NULL,
    name              VARCHAR(100) NOT NULL,
    workflow_id       BIGINT       NOT NULL REFERENCES workflow (id),
    organization_id   BIGINT       REFERENCES organization (id),
    is_template       BOOLEAN      NOT NULL DEFAULT FALSE,
    requires_proposal BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by_id     BIGINT       REFERENCES app_user (id),
    UNIQUE (organization_id, key)
);

CREATE INDEX idx_workflow_organization_id ON workflow (organization_id);
CREATE INDEX idx_workflow_state_workflow_id ON workflow_state (workflow_id);
CREATE INDEX idx_workflow_transition_workflow_id ON workflow_transition (workflow_id);
CREATE INDEX idx_ticket_type_organization_id ON ticket_type (organization_id);

INSERT INTO workflow (name, organization_id) VALUES
    ('Change Request Workflow', NULL),
    ('Task Workflow', NULL),
    ('Defect Workflow', NULL);

INSERT INTO workflow_state (workflow_id, key, is_initial, is_terminal, sort_order)
SELECT w.id, s.key, s.is_initial, s.is_terminal, s.sort_order
FROM workflow w
JOIN (
    VALUES
        ('Change Request Workflow', 'SUBMITTED', TRUE, FALSE, 10),
        ('Change Request Workflow', 'ANALYSIS', FALSE, FALSE, 20),
        ('Change Request Workflow', 'PROPOSAL', FALSE, FALSE, 30),
        ('Change Request Workflow', 'PROPOSAL_REJECTED', FALSE, FALSE, 40),
        ('Change Request Workflow', 'PROPOSAL_APPROVED', FALSE, FALSE, 50),
        ('Change Request Workflow', 'DEVELOPMENT', FALSE, FALSE, 60),
        ('Change Request Workflow', 'FIRST_OCCURRENCE_TESTING', FALSE, FALSE, 70),
        ('Change Request Workflow', 'USER_ACCEPTANCE_TESTING', FALSE, FALSE, 80),
        ('Change Request Workflow', 'READY_FOR_PRODUCTION', FALSE, FALSE, 90),
        ('Change Request Workflow', 'IN_PRODUCTION', FALSE, FALSE, 100),
        ('Change Request Workflow', 'CLOSED', FALSE, TRUE, 110),
        ('Change Request Workflow', 'CANCELLED', FALSE, TRUE, 120),
        ('Task Workflow', 'SUBMITTED', TRUE, FALSE, 10),
        ('Task Workflow', 'ANALYSIS', FALSE, FALSE, 20),
        ('Task Workflow', 'DEVELOPMENT', FALSE, FALSE, 30),
        ('Task Workflow', 'FIRST_OCCURRENCE_TESTING', FALSE, FALSE, 40),
        ('Task Workflow', 'USER_ACCEPTANCE_TESTING', FALSE, FALSE, 50),
        ('Task Workflow', 'READY_FOR_PRODUCTION', FALSE, FALSE, 60),
        ('Task Workflow', 'IN_PRODUCTION', FALSE, FALSE, 70),
        ('Task Workflow', 'CLOSED', FALSE, TRUE, 80),
        ('Task Workflow', 'CANCELLED', FALSE, TRUE, 90),
        ('Defect Workflow', 'REPORTED', TRUE, FALSE, 10),
        ('Defect Workflow', 'ANALYSIS', FALSE, FALSE, 20),
        ('Defect Workflow', 'FIX_IN_PROGRESS', FALSE, FALSE, 30),
        ('Defect Workflow', 'CLIENT_CONFIRMATION', FALSE, FALSE, 40),
        ('Defect Workflow', 'CLOSED', FALSE, TRUE, 50),
        ('Defect Workflow', 'CANCELLED', FALSE, TRUE, 60)
) AS s(workflow_name, key, is_initial, is_terminal, sort_order)
    ON s.workflow_name = w.name;

INSERT INTO workflow_transition (
    workflow_id, from_state_id, to_state_id, required_permission_id, required_party, responsibility_after
)
SELECT w.id, fs.id, ts.id, p.id, t.required_party, t.responsibility_after
FROM (
    VALUES
        ('Change Request Workflow', 'SUBMITTED', 'ANALYSIS', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Change Request Workflow', 'SUBMITTED', 'CANCELLED', 'TICKET_CANCEL', 'TICKETFLOW1', NULL),
        ('Change Request Workflow', 'ANALYSIS', 'PROPOSAL', 'TICKET_TRANSITION', 'TICKETFLOW1', 'CLIENT'),
        ('Change Request Workflow', 'ANALYSIS', 'CANCELLED', 'TICKET_CANCEL', 'TICKETFLOW1', NULL),
        ('Change Request Workflow', 'PROPOSAL', 'PROPOSAL_APPROVED', 'PROPOSAL_APPROVE', 'CLIENT', 'TICKETFLOW1'),
        ('Change Request Workflow', 'PROPOSAL', 'PROPOSAL_REJECTED', 'PROPOSAL_APPROVE', 'CLIENT', 'TICKETFLOW1'),
        ('Change Request Workflow', 'PROPOSAL_REJECTED', 'ANALYSIS', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Change Request Workflow', 'PROPOSAL_REJECTED', 'CANCELLED', 'TICKET_CANCEL', 'TICKETFLOW1', NULL),
        ('Change Request Workflow', 'PROPOSAL_APPROVED', 'DEVELOPMENT', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Change Request Workflow', 'DEVELOPMENT', 'FIRST_OCCURRENCE_TESTING', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Change Request Workflow', 'FIRST_OCCURRENCE_TESTING', 'USER_ACCEPTANCE_TESTING', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Change Request Workflow', 'USER_ACCEPTANCE_TESTING', 'READY_FOR_PRODUCTION', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Change Request Workflow', 'READY_FOR_PRODUCTION', 'IN_PRODUCTION', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Change Request Workflow', 'IN_PRODUCTION', 'CLOSED', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Task Workflow', 'SUBMITTED', 'ANALYSIS', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Task Workflow', 'SUBMITTED', 'CANCELLED', 'TICKET_CANCEL', 'TICKETFLOW1', NULL),
        ('Task Workflow', 'ANALYSIS', 'DEVELOPMENT', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Task Workflow', 'ANALYSIS', 'CANCELLED', 'TICKET_CANCEL', 'TICKETFLOW1', NULL),
        ('Task Workflow', 'DEVELOPMENT', 'FIRST_OCCURRENCE_TESTING', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Task Workflow', 'FIRST_OCCURRENCE_TESTING', 'USER_ACCEPTANCE_TESTING', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Task Workflow', 'USER_ACCEPTANCE_TESTING', 'READY_FOR_PRODUCTION', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Task Workflow', 'READY_FOR_PRODUCTION', 'IN_PRODUCTION', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Task Workflow', 'IN_PRODUCTION', 'CLOSED', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Defect Workflow', 'REPORTED', 'ANALYSIS', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Defect Workflow', 'REPORTED', 'CANCELLED', 'TICKET_CANCEL', 'TICKETFLOW1', NULL),
        ('Defect Workflow', 'ANALYSIS', 'FIX_IN_PROGRESS', 'TICKET_TRANSITION', 'TICKETFLOW1', NULL),
        ('Defect Workflow', 'ANALYSIS', 'CANCELLED', 'TICKET_CANCEL', 'TICKETFLOW1', NULL),
        ('Defect Workflow', 'FIX_IN_PROGRESS', 'CLIENT_CONFIRMATION', 'TICKET_TRANSITION', 'TICKETFLOW1', 'CLIENT'),
        ('Defect Workflow', 'CLIENT_CONFIRMATION', 'CLOSED', 'TICKET_TRANSITION', 'CLIENT', NULL),
        ('Defect Workflow', 'CLIENT_CONFIRMATION', 'FIX_IN_PROGRESS', 'TICKET_TRANSITION', 'CLIENT', 'TICKETFLOW1')
) AS t(workflow_name, from_key, to_key, permission_key, required_party, responsibility_after)
JOIN workflow w ON w.name = t.workflow_name
JOIN workflow_state fs ON fs.workflow_id = w.id AND fs.key = t.from_key
JOIN workflow_state ts ON ts.workflow_id = w.id AND ts.key = t.to_key
JOIN permission p ON p.key = t.permission_key;

INSERT INTO ticket_type (key, name, workflow_id, organization_id, is_template, requires_proposal)
SELECT tt.key, tt.name, w.id, NULL, TRUE, tt.requires_proposal
FROM (
    VALUES
        ('CHANGE_REQUEST', 'Change Request', 'Change Request Workflow', TRUE),
        ('TASK', 'Task', 'Task Workflow', FALSE),
        ('DEFECT', 'Defect', 'Defect Workflow', FALSE)
) AS tt(key, name, workflow_name, requires_proposal)
JOIN workflow w ON w.name = tt.workflow_name;

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
    SELECT w.name, target_org_id
    FROM workflow w
    WHERE w.organization_id IS NULL
      AND NOT EXISTS (
          SELECT 1
          FROM workflow existing
          WHERE existing.organization_id = target_org_id
            AND existing.name = w.name
      );

    INSERT INTO workflow_clone_map (template_id, cloned_id)
    SELECT template.id, cloned.id
    FROM workflow template
    JOIN workflow cloned
        ON cloned.organization_id = target_org_id
       AND cloned.name = template.name
    WHERE template.organization_id IS NULL;

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
    WHERE existing.id IS NULL;

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
    WHERE existing.id IS NULL;

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
      AND existing.id IS NULL;
END;
$$;

SELECT clone_org_templates(id) FROM organization;
