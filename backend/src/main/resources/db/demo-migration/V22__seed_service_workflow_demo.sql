-- Demo-only service workflow scenario. This depends on production migrations
-- V16-V21 and is loaded only by the Spring "demo" profile.

-- Team used by TASI/USR routing examples.
INSERT INTO developer_team (name, description, leader_id, created_by_id)
SELECT 'Service Workflow Demo Team',
       'Demo team used for subtype routing and queue ordering.',
       manager.id,
       admin.id
FROM app_user manager
JOIN app_user admin ON admin.email = 'admin@ticketflow1.demo'
WHERE manager.email = 'manager@ticketflow1.demo'
  AND NOT EXISTS (
      SELECT 1 FROM developer_team WHERE name = 'Service Workflow Demo Team'
  );

INSERT INTO developer_team_member (team_id, user_id)
SELECT team.id, user_account.id
FROM developer_team team
JOIN app_user user_account ON user_account.email IN (
    'agent@ticketflow1.demo',
    'manager@ticketflow1.demo'
)
WHERE team.name = 'Service Workflow Demo Team'
ON CONFLICT DO NOTHING;

-- Runtime-configured fields for TASI/FIREWALL.
INSERT INTO subtype_field_definition (
    subtype_id, key, label, help_text, field_kind, required, visibility,
    sort_order, min_length, max_length
)
SELECT subtype.id, field.key, field.label, field.help_text, field.kind,
       field.required, 'INTERNAL', field.sort_order, field.min_length,
       field.max_length
FROM organization org
JOIN ticket_type type ON type.organization_id = org.id AND type.key = 'TASI'
JOIN ticket_subtype subtype ON subtype.ticket_type_id = type.id
JOIN (VALUES
    ('FIREWALL','source_cidr','Source CIDR','Source network or host that needs access.','SHORT_TEXT',true,10,3,120),
    ('FIREWALL','destination','Destination','Target host, service, or system.','SHORT_TEXT',true,20,3,120),
    ('FIREWALL','service_ports','Service ports','Protocol and port list, for example TCP/443.','SHORT_TEXT',true,30,3,120),
    ('FIREWALL','environment','Environment','Target environment.','SINGLE_SELECT',true,40,NULL,NULL),
    ('FIREWALL','business_justification','Business justification','Why this access is needed.','LONG_TEXT',true,50,10,1000)
) AS field(subtype_key, key, label, help_text, kind, required, sort_order, min_length, max_length)
  ON field.subtype_key = subtype.key
WHERE org.name = 'Alpine Retail'
  AND NOT EXISTS (
      SELECT 1 FROM subtype_field_definition existing
      WHERE existing.subtype_id = subtype.id AND existing.key = field.key
  );

INSERT INTO subtype_field_option (field_definition_id, key, label, sort_order)
SELECT field.id, option.key, option.label, option.sort_order
FROM organization org
JOIN ticket_type type ON type.organization_id = org.id AND type.key = 'TASI'
JOIN ticket_subtype subtype ON subtype.ticket_type_id = type.id AND subtype.key = 'FIREWALL'
JOIN subtype_field_definition field ON field.subtype_id = subtype.id AND field.key = 'environment'
JOIN (VALUES
    ('PRODUCTION','Production',10),
    ('NON_PRODUCTION','Non-production',20)
) AS option(key, label, sort_order) ON TRUE
WHERE org.name = 'Alpine Retail'
  AND NOT EXISTS (
      SELECT 1 FROM subtype_field_option existing
      WHERE existing.field_definition_id = field.id AND existing.key = option.key
  );

-- Runtime-configured field for USR/MODIFY.
INSERT INTO subtype_field_definition (
    subtype_id, key, label, help_text, field_kind, required, visibility,
    sort_order, min_length, max_length
)
SELECT subtype.id, 'change_summary', 'Change summary',
       'Describe what should change for the selected user.',
       'LONG_TEXT', true, 'INTERNAL', 10, 10, 1000
FROM organization org
JOIN ticket_type type ON type.organization_id = org.id AND type.key = 'USR'
JOIN ticket_subtype subtype ON subtype.ticket_type_id = type.id AND subtype.key = 'MODIFY'
WHERE org.name = 'Alpine Retail'
  AND NOT EXISTS (
      SELECT 1 FROM subtype_field_definition existing
      WHERE existing.subtype_id = subtype.id AND existing.key = 'change_summary'
  );

-- Automatic subtype routing examples.
INSERT INTO subtype_routing_rule (
    subtype_id, organization_id, team_id, primary_developer_id,
    fallback_developer_id, approver_id, active
)
SELECT subtype.id, org.id, team.id, agent.id, manager.id, manager.id, true
FROM organization org
JOIN ticket_type type ON type.organization_id = org.id AND type.key = 'TASI'
JOIN ticket_subtype subtype ON subtype.ticket_type_id = type.id AND subtype.key = 'FIREWALL'
JOIN developer_team team ON team.name = 'Service Workflow Demo Team'
JOIN app_user agent ON agent.email = 'agent@ticketflow1.demo'
JOIN app_user manager ON manager.email = 'manager@ticketflow1.demo'
WHERE org.name = 'Alpine Retail'
  AND NOT EXISTS (
      SELECT 1 FROM subtype_routing_rule existing
      WHERE existing.subtype_id = subtype.id
        AND existing.organization_id = org.id
        AND existing.active
  );

INSERT INTO subtype_routing_rule (
    subtype_id, organization_id, team_id, primary_developer_id,
    fallback_developer_id, approver_id, active
)
SELECT subtype.id, org.id, team.id, agent.id, manager.id, manager.id, true
FROM organization org
JOIN ticket_type type ON type.organization_id = org.id AND type.key = 'USR'
JOIN ticket_subtype subtype ON subtype.ticket_type_id = type.id AND subtype.key = 'MODIFY'
JOIN developer_team team ON team.name = 'Service Workflow Demo Team'
JOIN app_user agent ON agent.email = 'agent@ticketflow1.demo'
JOIN app_user manager ON manager.email = 'manager@ticketflow1.demo'
WHERE org.name = 'Alpine Retail'
  AND NOT EXISTS (
      SELECT 1 FROM subtype_routing_rule existing
      WHERE existing.subtype_id = subtype.id
        AND existing.organization_id = org.id
        AND existing.active
  );

-- Demo TASI awaiting protected approval.
INSERT INTO ticket (
    ticket_key, ticket_type_id, current_state_id, priority, severity,
    title, description, organization_id, business_owner_id, ticket_lead_id,
    assigned_team, current_responsibility, created_at, updated_at,
    subtype_id, routing_rule_id, resolved_approver_id
)
SELECT 'TF-2100', type.id, state.id, 'HIGH', NULL,
       'Demo firewall access for payroll API',
       'Presentation ticket showing runtime fields, routing, and approval.',
       org.id, owner.id, agent.id, team.name, 'TICKETFLOW1',
       now() - interval '2 hours', now() - interval '30 minutes',
       subtype.id, routing.id, manager.id
FROM organization org
JOIN ticket_type type ON type.organization_id = org.id AND type.key = 'TASI'
JOIN workflow_state state ON state.workflow_id = type.workflow_id AND state.key = 'PENDING_APPROVAL'
JOIN ticket_subtype subtype ON subtype.ticket_type_id = type.id AND subtype.key = 'FIREWALL'
JOIN subtype_routing_rule routing ON routing.subtype_id = subtype.id
    AND routing.organization_id = org.id AND routing.active
JOIN developer_team team ON team.id = routing.team_id
JOIN app_user owner ON owner.email = 'contributor@alpine.demo'
JOIN app_user agent ON agent.email = 'agent@ticketflow1.demo'
JOIN app_user manager ON manager.email = 'manager@ticketflow1.demo'
WHERE org.name = 'Alpine Retail'
  AND NOT EXISTS (SELECT 1 FROM ticket WHERE ticket_key = 'TF-2100');

-- Demo subticket under the TASI parent.
INSERT INTO ticket (
    ticket_key, ticket_type_id, current_state_id, priority, severity,
    title, description, organization_id, business_owner_id, ticket_lead_id,
    assigned_team, current_responsibility, created_at, updated_at,
    subtype_id, parent_ticket_id
)
SELECT 'TF-2101', type.id, state.id, 'MEDIUM', NULL,
       'Demo network review subticket',
       'Child ticket used to show parent/subticket navigation and progress.',
       org.id, owner.id, agent.id, 'Service Workflow Demo Team', 'TICKETFLOW1',
       now() - interval '90 minutes', now() - interval '45 minutes',
       subtype.id, parent.id
FROM organization org
JOIN ticket_type type ON type.organization_id = org.id AND type.key = 'TASI'
JOIN workflow_state state ON state.workflow_id = type.workflow_id AND state.key = 'ANALYSIS'
JOIN ticket_subtype subtype ON subtype.ticket_type_id = type.id AND subtype.key = 'NETWORK'
JOIN ticket parent ON parent.ticket_key = 'TF-2100'
JOIN app_user owner ON owner.email = 'contributor@alpine.demo'
JOIN app_user agent ON agent.email = 'agent@ticketflow1.demo'
WHERE org.name = 'Alpine Retail'
  AND NOT EXISTS (SELECT 1 FROM ticket WHERE ticket_key = 'TF-2101');

-- Demo REQ awaiting client acceptance by the Alpine approver.
INSERT INTO ticket (
    ticket_key, ticket_type_id, current_state_id, priority, severity,
    title, description, organization_id, business_owner_id, ticket_lead_id,
    assigned_team, current_responsibility, created_at, updated_at,
    client_acceptance_approver_id
)
SELECT 'TF-2102', type.id, state.id, 'MEDIUM', NULL,
       'Demo client acceptance for invoice export',
       'Presentation ticket showing REQ client acceptance before deployment.',
       org.id, owner.id, agent.id, 'Service Workflow Demo Team', 'CLIENT',
       now() - interval '1 day', now() - interval '1 hour', approver.id
FROM organization org
JOIN ticket_type type ON type.organization_id = org.id AND type.key = 'REQ'
JOIN workflow_state state ON state.workflow_id = type.workflow_id AND state.key = 'CLIENT_ACCEPTANCE'
JOIN app_user owner ON owner.email = 'contributor@alpine.demo'
JOIN app_user approver ON approver.email = 'approver@alpine.demo'
JOIN app_user agent ON agent.email = 'agent@ticketflow1.demo'
WHERE org.name = 'Alpine Retail'
  AND NOT EXISTS (SELECT 1 FROM ticket WHERE ticket_key = 'TF-2102');

-- Demo USR/MODIFY with target-user snapshot.
INSERT INTO ticket (
    ticket_key, ticket_type_id, current_state_id, priority, severity,
    title, description, organization_id, business_owner_id, ticket_lead_id,
    assigned_team, current_responsibility, created_at, updated_at,
    subtype_id, routing_rule_id, resolved_approver_id,
    target_user_id, target_user_display_snapshot
)
SELECT 'TF-2103', type.id, state.id, 'MEDIUM', NULL,
       'Demo modify user access',
       'Presentation ticket showing USR target-user search and stored snapshot.',
       org.id, owner.id, agent.id, team.name, 'TICKETFLOW1',
       now() - interval '3 hours', now() - interval '2 hours',
       subtype.id, routing.id, manager.id,
       target_user.id, target_user.display_name
FROM organization org
JOIN ticket_type type ON type.organization_id = org.id AND type.key = 'USR'
JOIN workflow_state state ON state.workflow_id = type.workflow_id AND state.key = 'ANALYSIS'
JOIN ticket_subtype subtype ON subtype.ticket_type_id = type.id AND subtype.key = 'MODIFY'
JOIN subtype_routing_rule routing ON routing.subtype_id = subtype.id
    AND routing.organization_id = org.id AND routing.active
JOIN developer_team team ON team.id = routing.team_id
JOIN app_user owner ON owner.email = 'contributor@alpine.demo'
JOIN app_user target_user ON target_user.email = 'contributor@alpine.demo'
JOIN app_user agent ON agent.email = 'agent@ticketflow1.demo'
JOIN app_user manager ON manager.email = 'manager@ticketflow1.demo'
WHERE org.name = 'Alpine Retail'
  AND NOT EXISTS (SELECT 1 FROM ticket WHERE ticket_key = 'TF-2103');

INSERT INTO ticket_developer (ticket_id, user_id)
SELECT ticket.id, agent.id
FROM ticket
JOIN app_user agent ON agent.email = 'agent@ticketflow1.demo'
WHERE ticket.ticket_key IN ('TF-2100','TF-2101','TF-2102','TF-2103')
ON CONFLICT DO NOTHING;

INSERT INTO developer_team_ticket (team_id, ticket_id, sort_order)
SELECT team.id, ticket.id,
       CASE ticket.ticket_key
           WHEN 'TF-2100' THEN 10
           WHEN 'TF-2101' THEN 20
           WHEN 'TF-2102' THEN 30
           ELSE 40
       END
FROM developer_team team
JOIN ticket ON ticket.ticket_key IN ('TF-2100','TF-2101','TF-2102','TF-2103')
WHERE team.name = 'Service Workflow Demo Team'
  AND NOT EXISTS (
      SELECT 1 FROM developer_team_ticket existing
      WHERE existing.team_id = team.id
        AND existing.ticket_id = ticket.id
  );

-- Dynamic values for TF-2100 TASI/FIREWALL.
INSERT INTO ticket_field_value (ticket_id, field_definition_id, text_value)
SELECT ticket.id, field.id, value.text_value
FROM ticket
JOIN subtype_field_definition field ON field.subtype_id = ticket.subtype_id
JOIN (VALUES
    ('source_cidr','10.20.0.0/24'),
    ('destination','payroll.internal'),
    ('service_ports','TCP/443'),
    ('business_justification','Payroll integration must reach the internal API before month-end close.')
) AS value(field_key, text_value) ON value.field_key = field.key
WHERE ticket.ticket_key = 'TF-2100'
  AND NOT EXISTS (
      SELECT 1 FROM ticket_field_value existing
      WHERE existing.ticket_id = ticket.id
        AND existing.field_definition_id = field.id
  );

INSERT INTO ticket_field_value (ticket_id, field_definition_id, selected_option_id)
SELECT ticket.id, field.id, option.id
FROM ticket
JOIN subtype_field_definition field ON field.subtype_id = ticket.subtype_id
    AND field.key = 'environment'
JOIN subtype_field_option option ON option.field_definition_id = field.id
    AND option.key = 'PRODUCTION'
WHERE ticket.ticket_key = 'TF-2100'
  AND NOT EXISTS (
      SELECT 1 FROM ticket_field_value existing
      WHERE existing.ticket_id = ticket.id
        AND existing.field_definition_id = field.id
  );

-- Dynamic value for TF-2103 USR/MODIFY.
INSERT INTO ticket_field_value (ticket_id, field_definition_id, text_value)
SELECT ticket.id, field.id,
       'Add reporting dashboard access and remove obsolete staging access.'
FROM ticket
JOIN subtype_field_definition field ON field.subtype_id = ticket.subtype_id
    AND field.key = 'change_summary'
WHERE ticket.ticket_key = 'TF-2103'
  AND NOT EXISTS (
      SELECT 1 FROM ticket_field_value existing
      WHERE existing.ticket_id = ticket.id
        AND existing.field_definition_id = field.id
  );

INSERT INTO status_history (ticket_id, from_state_id, to_state_id, changed_by_id, created_at)
SELECT ticket.id, NULL, ticket.current_state_id, ticket.business_owner_id, ticket.created_at
FROM ticket
WHERE ticket.ticket_key IN ('TF-2100','TF-2101','TF-2102','TF-2103')
  AND NOT EXISTS (
      SELECT 1 FROM status_history existing WHERE existing.ticket_id = ticket.id
  );

INSERT INTO audit_log (ticket_id, actor_id, action, new_value, created_at)
SELECT ticket.id, ticket.business_owner_id, 'TICKET_CREATED', ticket.ticket_key, ticket.created_at
FROM ticket
WHERE ticket.ticket_key IN ('TF-2100','TF-2101','TF-2102','TF-2103')
  AND NOT EXISTS (
      SELECT 1 FROM audit_log existing
      WHERE existing.ticket_id = ticket.id AND existing.action = 'TICKET_CREATED'
  );

INSERT INTO audit_log (ticket_id, actor_id, action, field_name, new_value, created_at)
SELECT ticket.id, ticket.business_owner_id, 'DYNAMIC_FIELDS_CAPTURED',
       'dynamicValues', 'captured', ticket.created_at
FROM ticket
WHERE ticket.ticket_key IN ('TF-2100','TF-2103')
  AND NOT EXISTS (
      SELECT 1 FROM audit_log existing
      WHERE existing.ticket_id = ticket.id AND existing.action = 'DYNAMIC_FIELDS_CAPTURED'
  );

SELECT setval('ticket_key_seq', 2103, true);
