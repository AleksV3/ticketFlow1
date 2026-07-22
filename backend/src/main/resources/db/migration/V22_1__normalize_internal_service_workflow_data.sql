-- Normalize ordered team tickets and ensure internal TASI/USR work uses the
-- TicketFlow1 Internal organization. This is intentionally idempotent so it
-- repairs demo/local databases that already received the first service-workflow
-- seed, while remaining harmless for production databases without demo tickets.

WITH ranked AS (
    SELECT team_id, ticket_id,
           row_number() OVER (PARTITION BY team_id ORDER BY sort_order, ticket_id) - 1 AS new_sort_order
    FROM developer_team_ticket
)
UPDATE developer_team_ticket target
SET sort_order = ranked.new_sort_order
FROM ranked
WHERE target.team_id = ranked.team_id
  AND target.ticket_id = ranked.ticket_id
  AND target.sort_order <> ranked.new_sort_order;

-- Internal runtime-configured fields for TASI/FIREWALL.
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
WHERE org.name = 'TicketFlow1 Internal'
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
WHERE org.name = 'TicketFlow1 Internal'
  AND NOT EXISTS (
      SELECT 1 FROM subtype_field_option existing
      WHERE existing.field_definition_id = field.id AND existing.key = option.key
  );

-- Internal runtime-configured field for USR/MODIFY.
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
WHERE org.name = 'TicketFlow1 Internal'
  AND NOT EXISTS (
      SELECT 1 FROM subtype_field_definition existing
      WHERE existing.subtype_id = subtype.id AND existing.key = 'change_summary'
  );

-- Internal automatic routing examples for TASI/FIREWALL and USR/MODIFY.
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
WHERE org.name = 'TicketFlow1 Internal'
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
WHERE org.name = 'TicketFlow1 Internal'
  AND NOT EXISTS (
      SELECT 1 FROM subtype_routing_rule existing
      WHERE existing.subtype_id = subtype.id
        AND existing.organization_id = org.id
        AND existing.active
  );

-- Move existing demo internal workflow tickets from the Alpine clone to the
-- internal clone. Parent links remain cross-organization-safe.
UPDATE ticket ticket_row
SET organization_id = org.id,
    business_owner_id = admin.id,
    ticket_type_id = type.id,
    current_state_id = state.id,
    subtype_id = subtype.id,
    routing_rule_id = routing.id,
    resolved_approver_id = manager.id,
    ticket_lead_id = agent.id,
    assigned_team = team.name
FROM organization org
JOIN ticket_type type ON type.organization_id = org.id AND type.key = 'TASI'
JOIN workflow_state state ON state.workflow_id = type.workflow_id AND state.key = 'PENDING_APPROVAL'
JOIN ticket_subtype subtype ON subtype.ticket_type_id = type.id AND subtype.key = 'FIREWALL'
JOIN subtype_routing_rule routing ON routing.subtype_id = subtype.id
    AND routing.organization_id = org.id AND routing.active
JOIN developer_team team ON team.id = routing.team_id
JOIN app_user admin ON admin.email = 'admin@ticketflow1.demo'
JOIN app_user agent ON agent.email = 'agent@ticketflow1.demo'
JOIN app_user manager ON manager.email = 'manager@ticketflow1.demo'
WHERE org.name = 'TicketFlow1 Internal'
  AND ticket_row.ticket_key = 'TF-2100';

UPDATE ticket ticket_row
SET organization_id = org.id,
    business_owner_id = admin.id,
    ticket_type_id = type.id,
    current_state_id = state.id,
    subtype_id = subtype.id,
    ticket_lead_id = agent.id,
    assigned_team = 'Service Workflow Demo Team'
FROM organization org
JOIN ticket_type type ON type.organization_id = org.id AND type.key = 'TASI'
JOIN workflow_state state ON state.workflow_id = type.workflow_id AND state.key = 'ANALYSIS'
JOIN ticket_subtype subtype ON subtype.ticket_type_id = type.id AND subtype.key = 'NETWORK'
JOIN app_user admin ON admin.email = 'admin@ticketflow1.demo'
JOIN app_user agent ON agent.email = 'agent@ticketflow1.demo'
WHERE org.name = 'TicketFlow1 Internal'
  AND ticket_row.ticket_key = 'TF-2101';

UPDATE ticket ticket_row
SET organization_id = org.id,
    business_owner_id = admin.id,
    ticket_type_id = type.id,
    current_state_id = state.id,
    subtype_id = subtype.id,
    routing_rule_id = routing.id,
    resolved_approver_id = manager.id,
    ticket_lead_id = agent.id,
    assigned_team = team.name
FROM organization org
JOIN ticket_type type ON type.organization_id = org.id AND type.key = 'USR'
JOIN workflow_state state ON state.workflow_id = type.workflow_id AND state.key = 'ANALYSIS'
JOIN ticket_subtype subtype ON subtype.ticket_type_id = type.id AND subtype.key = 'MODIFY'
JOIN subtype_routing_rule routing ON routing.subtype_id = subtype.id
    AND routing.organization_id = org.id AND routing.active
JOIN developer_team team ON team.id = routing.team_id
JOIN app_user admin ON admin.email = 'admin@ticketflow1.demo'
JOIN app_user agent ON agent.email = 'agent@ticketflow1.demo'
JOIN app_user manager ON manager.email = 'manager@ticketflow1.demo'
WHERE org.name = 'TicketFlow1 Internal'
  AND ticket_row.ticket_key = 'TF-2103';

DELETE FROM ticket_field_value
WHERE ticket_id IN (
    SELECT id FROM ticket WHERE ticket_key IN ('TF-2100', 'TF-2103')
);

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
WHERE ticket.ticket_key = 'TF-2100';

INSERT INTO ticket_field_value (ticket_id, field_definition_id, selected_option_id)
SELECT ticket.id, field.id, option.id
FROM ticket
JOIN subtype_field_definition field ON field.subtype_id = ticket.subtype_id
    AND field.key = 'environment'
JOIN subtype_field_option option ON option.field_definition_id = field.id
    AND option.key = 'PRODUCTION'
WHERE ticket.ticket_key = 'TF-2100';

INSERT INTO ticket_field_value (ticket_id, field_definition_id, text_value)
SELECT ticket.id, field.id,
       'Add reporting dashboard access and remove obsolete staging access.'
FROM ticket
JOIN subtype_field_definition field ON field.subtype_id = ticket.subtype_id
    AND field.key = 'change_summary'
WHERE ticket.ticket_key = 'TF-2103';
