-- Previous versions of the workflow admin page edited organization_id NULL
-- template ticket types when "TicketFlow1 Internal" was selected. Runtime
-- ticket creation uses the real TicketFlow1 Internal organization clone, so
-- subtype form fields and routing saved on the template were invisible. Copy
-- any template-side subtype configuration into the internal organization clone.

INSERT INTO ticket_subtype (ticket_type_id, key, name, description, active, sort_order)
SELECT internal_type.id, template_subtype.key, template_subtype.name,
       template_subtype.description, template_subtype.active, template_subtype.sort_order
FROM organization internal_org
JOIN ticket_type internal_type ON internal_type.organization_id = internal_org.id
JOIN ticket_type template_type ON template_type.organization_id IS NULL
    AND template_type.key = internal_type.key
JOIN ticket_subtype template_subtype ON template_subtype.ticket_type_id = template_type.id
WHERE internal_org.name = 'TicketFlow1 Internal'
  AND NOT EXISTS (
      SELECT 1 FROM ticket_subtype existing
      WHERE existing.ticket_type_id = internal_type.id
        AND existing.key = template_subtype.key
  );

INSERT INTO subtype_field_definition (
    subtype_id, key, label, help_text, field_kind, required, visibility,
    active, sort_order, min_length, max_length, min_number, max_number
)
SELECT internal_subtype.id, template_field.key, template_field.label,
       template_field.help_text, template_field.field_kind, template_field.required,
       template_field.visibility, template_field.active, template_field.sort_order,
       template_field.min_length, template_field.max_length,
       template_field.min_number, template_field.max_number
FROM organization internal_org
JOIN ticket_type internal_type ON internal_type.organization_id = internal_org.id
JOIN ticket_type template_type ON template_type.organization_id IS NULL
    AND template_type.key = internal_type.key
JOIN ticket_subtype template_subtype ON template_subtype.ticket_type_id = template_type.id
JOIN ticket_subtype internal_subtype ON internal_subtype.ticket_type_id = internal_type.id
    AND internal_subtype.key = template_subtype.key
JOIN subtype_field_definition template_field ON template_field.subtype_id = template_subtype.id
WHERE internal_org.name = 'TicketFlow1 Internal'
  AND NOT EXISTS (
      SELECT 1 FROM subtype_field_definition existing
      WHERE existing.subtype_id = internal_subtype.id
        AND existing.key = template_field.key
  );

INSERT INTO subtype_field_option (
    field_definition_id, key, label, active, sort_order
)
SELECT internal_field.id, template_option.key, template_option.label,
       template_option.active, template_option.sort_order
FROM organization internal_org
JOIN ticket_type internal_type ON internal_type.organization_id = internal_org.id
JOIN ticket_type template_type ON template_type.organization_id IS NULL
    AND template_type.key = internal_type.key
JOIN ticket_subtype template_subtype ON template_subtype.ticket_type_id = template_type.id
JOIN ticket_subtype internal_subtype ON internal_subtype.ticket_type_id = internal_type.id
    AND internal_subtype.key = template_subtype.key
JOIN subtype_field_definition template_field ON template_field.subtype_id = template_subtype.id
JOIN subtype_field_definition internal_field ON internal_field.subtype_id = internal_subtype.id
    AND internal_field.key = template_field.key
JOIN subtype_field_option template_option ON template_option.field_definition_id = template_field.id
WHERE internal_org.name = 'TicketFlow1 Internal'
  AND NOT EXISTS (
      SELECT 1 FROM subtype_field_option existing
      WHERE existing.field_definition_id = internal_field.id
        AND existing.key = template_option.key
  );

INSERT INTO subtype_routing_rule (
    subtype_id, organization_id, team_id, primary_developer_id,
    fallback_developer_id, approver_id, active
)
SELECT internal_subtype.id, internal_org.id, template_routing.team_id,
       template_routing.primary_developer_id, template_routing.fallback_developer_id,
       template_routing.approver_id, template_routing.active
FROM organization internal_org
JOIN ticket_type internal_type ON internal_type.organization_id = internal_org.id
JOIN ticket_type template_type ON template_type.organization_id IS NULL
    AND template_type.key = internal_type.key
JOIN ticket_subtype template_subtype ON template_subtype.ticket_type_id = template_type.id
JOIN ticket_subtype internal_subtype ON internal_subtype.ticket_type_id = internal_type.id
    AND internal_subtype.key = template_subtype.key
JOIN subtype_routing_rule template_routing ON template_routing.subtype_id = template_subtype.id
    AND template_routing.organization_id IS NULL
WHERE internal_org.name = 'TicketFlow1 Internal'
  AND template_routing.active
  AND NOT EXISTS (
      SELECT 1 FROM subtype_routing_rule existing
      WHERE existing.subtype_id = internal_subtype.id
        AND existing.organization_id = internal_org.id
        AND existing.active
  );

-- NETWORK existed as a starter subtype, but had no starter dynamic form.
-- These fields are ordinary editable dynamic fields, seeded only when missing.
INSERT INTO subtype_field_definition (
    subtype_id, key, label, help_text, field_kind, required, visibility,
    sort_order, min_length, max_length
)
SELECT subtype.id, field.key, field.label, field.help_text, field.kind,
       field.required, 'INTERNAL', field.sort_order, field.min_length,
       field.max_length
FROM organization internal_org
JOIN ticket_type type ON type.organization_id = internal_org.id AND type.key = 'TASI'
JOIN ticket_subtype subtype ON subtype.ticket_type_id = type.id AND subtype.key = 'NETWORK'
JOIN (VALUES
    ('network_area','Network area','VLAN, subnet, site, device, or network segment affected by this task.','SHORT_TEXT',true,10,2,120),
    ('requested_change','Requested change','Describe the network change or investigation needed.','LONG_TEXT',true,20,10,1000),
    ('affected_service','Affected service','Business service or system that depends on this network work.','SHORT_TEXT',false,30,2,120),
    ('maintenance_window','Maintenance window','Preferred date/time window for implementation.','SHORT_TEXT',false,40,2,120),
    ('rollback_plan','Rollback plan','How to revert the change if implementation fails.','LONG_TEXT',false,50,10,1000)
) AS field(key, label, help_text, kind, required, sort_order, min_length, max_length) ON TRUE
WHERE internal_org.name = 'TicketFlow1 Internal'
  AND NOT EXISTS (
      SELECT 1 FROM subtype_field_definition existing
      WHERE existing.subtype_id = subtype.id
        AND existing.key = field.key
  );
