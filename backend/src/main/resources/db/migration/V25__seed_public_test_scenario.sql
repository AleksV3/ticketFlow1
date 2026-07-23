-- Public test scenario for the internet-hosted app.
--
-- These accounts are intentionally separate from the demo profile. The shared
-- test password is communicated out-of-band; only the BCrypt hash is stored.

INSERT INTO organization (name)
SELECT 'Acme Field Services'
WHERE NOT EXISTS (SELECT 1 FROM organization WHERE name = 'Acme Field Services');

SELECT clone_org_templates(id)
FROM organization
WHERE name = 'Acme Field Services';

INSERT INTO app_user (email, password_hash, display_name, party, role_id, organization_id)
SELECT account.email,
       '$2b$10$FZ8WNA78vgbxN9URh86PlOK9qRkvCEwqE6iqs.8LP.oV/MKohpHJC',
       account.display_name,
       account.party,
       role.id,
       organization.id
FROM (VALUES
    ('test.admin@ticketflow1.app', 'TicketFlow1 Test Admin', 'TICKETFLOW1', 'Admin', NULL),
    ('test.manager@ticketflow1.app', 'Mila Operations Manager', 'TICKETFLOW1', 'TicketFlow1 Manager', NULL),
    ('test.developer@ticketflow1.app', 'Luka Platform Developer', 'TICKETFLOW1', 'TicketFlow1 User', NULL),
    ('owner@acme-test.app', 'Eva Business Owner', 'CLIENT', 'Client User', 'Acme Field Services'),
    ('approver@acme-test.app', 'Marko Client Approver', 'CLIENT', 'Client Approver', 'Acme Field Services'),
    ('requester@acme-test.app', 'Nina Store Requester', 'CLIENT', 'Client User', 'Acme Field Services')
) AS account(email, display_name, party, role_name, organization_name)
LEFT JOIN organization ON organization.name = account.organization_name
JOIN role ON role.name = account.role_name
 AND role.party = account.party
 AND role.organization_id IS NOT DISTINCT FROM organization.id
WHERE NOT EXISTS (SELECT 1 FROM app_user existing WHERE existing.email = account.email);

INSERT INTO app_user_role (user_id, role_id)
SELECT app_user.id, app_user.role_id
FROM app_user
WHERE app_user.email IN (
    'test.admin@ticketflow1.app',
    'test.manager@ticketflow1.app',
    'test.developer@ticketflow1.app',
    'owner@acme-test.app',
    'approver@acme-test.app',
    'requester@acme-test.app'
)
ON CONFLICT DO NOTHING;

INSERT INTO developer_team (name, description, leader_id, created_by_id)
SELECT team.name, team.description, leader.id, creator.id
FROM (VALUES
    ('Platform Operations', 'Handles infrastructure, firewall, network and platform incidents.', 'test.manager@ticketflow1.app'),
    ('Client Delivery', 'Handles client requests, deployments and acceptance follow-up.', 'test.developer@ticketflow1.app')
) AS team(name, description, leader_email)
JOIN app_user leader ON leader.email = team.leader_email
JOIN app_user creator ON creator.email = 'test.admin@ticketflow1.app'
WHERE NOT EXISTS (SELECT 1 FROM developer_team existing WHERE existing.name = team.name);

INSERT INTO developer_team_member (team_id, user_id)
SELECT developer_team.id, app_user.id
FROM developer_team
JOIN app_user ON app_user.email IN (
    'test.admin@ticketflow1.app',
    'test.manager@ticketflow1.app',
    'test.developer@ticketflow1.app'
)
WHERE developer_team.name IN ('Platform Operations', 'Client Delivery')
ON CONFLICT DO NOTHING;

INSERT INTO subtype_routing_rule (
    subtype_id, organization_id, team_id, primary_developer_id,
    fallback_developer_id, approver_id, active
)
SELECT subtype.id, organization.id, team.id, primary_dev.id, fallback_dev.id, approver.id, true
FROM (VALUES
    ('Acme Field Services', 'DFCT', 'NETWORK', 'Platform Operations', 'test.developer@ticketflow1.app', 'test.manager@ticketflow1.app', 'test.manager@ticketflow1.app'),
    ('Acme Field Services', 'REQ', 'APPLICATION', 'Client Delivery', 'test.developer@ticketflow1.app', 'test.manager@ticketflow1.app', 'test.manager@ticketflow1.app'),
    ('TicketFlow1 Internal', 'TASI', 'FIREWALL', 'Platform Operations', 'test.developer@ticketflow1.app', 'test.manager@ticketflow1.app', 'test.manager@ticketflow1.app'),
    ('TicketFlow1 Internal', 'USR', 'MODIFY', 'Client Delivery', 'test.developer@ticketflow1.app', 'test.manager@ticketflow1.app', 'test.manager@ticketflow1.app')
) AS route(organization_name, type_key, subtype_key, team_name, primary_email, fallback_email, approver_email)
JOIN organization ON organization.name = route.organization_name
JOIN ticket_type type ON type.organization_id = organization.id AND type.key = route.type_key
JOIN ticket_subtype subtype ON subtype.ticket_type_id = type.id AND subtype.key = route.subtype_key
JOIN developer_team team ON team.name = route.team_name
JOIN app_user primary_dev ON primary_dev.email = route.primary_email
JOIN app_user fallback_dev ON fallback_dev.email = route.fallback_email
JOIN app_user approver ON approver.email = route.approver_email
WHERE NOT EXISTS (
    SELECT 1 FROM subtype_routing_rule existing
    WHERE existing.subtype_id = subtype.id
      AND existing.organization_id IS NOT DISTINCT FROM organization.id
);

INSERT INTO ticket (
    ticket_key, ticket_type_id, current_state_id, priority, severity,
    title, description, organization_id, business_owner_id, ticket_lead_id,
    assigned_team, current_responsibility, subtype_id, target_user_id,
    target_user_display_snapshot, created_at, response_due_at,
    first_info_due_at, next_update_due_at
)
SELECT seed.ticket_key, type.id, state.id, seed.priority, seed.severity,
       seed.title, seed.description, organization.id, owner.id, lead.id,
       seed.assigned_team, seed.responsibility, subtype.id, target_user.id,
       seed.target_user_snapshot, seed.created_at, seed.response_due_at,
       seed.first_info_due_at, seed.next_update_due_at
FROM (VALUES
    ('TF-2000', 'Acme Field Services', 'owner@acme-test.app', 'DFCT', 'NETWORK', 'REPORTED',
     'CRITICAL', 'SEV_1', 'Warehouse scanner network outage',
     'Handheld scanners in warehouse B cannot reach the inventory API after the morning router restart.',
     'Platform Operations', 'TICKETFLOW1', NULL, NULL, now() - interval '70 minutes',
     now() - interval '10 minutes', now() + interval '50 minutes', now() + interval '20 minutes'),
    ('TF-2001', 'Acme Field Services', 'requester@acme-test.app', 'REQ', 'APPLICATION', 'ANALYSIS',
     'HIGH', NULL, 'Add delivery exception dashboard',
     'Operations needs a dashboard showing blocked deliveries grouped by depot and exception reason.',
     'Client Delivery', 'TICKETFLOW1', NULL, NULL, now() - interval '2 days',
     NULL, NULL, NULL),
    ('TF-2002', 'Acme Field Services', 'approver@acme-test.app', 'DFCT', 'NETWORK', 'ANALYSIS',
     'MEDIUM', 'SEV_3', 'Intermittent label printer disconnects',
     'Two depot label printers disconnect for a few minutes during peak packing windows.',
     'Platform Operations', 'TICKETFLOW1', NULL, NULL, now() - interval '8 hours',
     now() + interval '8 hours', now() + interval '20 hours', NULL),
    ('TF-2003', 'TicketFlow1 Internal', 'test.admin@ticketflow1.app', 'TASI', 'FIREWALL', 'ANALYSIS',
     'HIGH', NULL, 'Review VPN firewall rule for Acme',
     'Validate whether Acme warehouse VPN ranges should be added to the protected allow-list.',
     'Platform Operations', 'TICKETFLOW1', NULL, NULL, now() - interval '5 hours',
     NULL, NULL, NULL),
    ('TF-2004', 'TicketFlow1 Internal', 'test.manager@ticketflow1.app', 'USR', 'MODIFY', 'PENDING_APPROVAL',
     'MEDIUM', NULL, 'Modify Acme requester access',
     'Update requester profile so the user can see delivery exception requests but not admin workflow pages.',
     'Client Delivery', 'TICKETFLOW1', 'requester@acme-test.app', 'Nina Store Requester', now() - interval '1 day',
     NULL, NULL, NULL),
    ('TF-2005', 'Acme Field Services', 'owner@acme-test.app', 'REQ', 'APPLICATION', 'CLIENT_ACCEPTANCE',
     'LOW', NULL, 'Change report export filename',
     'Rename the daily export file so it includes organization key and report date.',
     'Client Delivery', 'CLIENT', NULL, NULL, now() - interval '4 days',
     NULL, NULL, NULL)
) AS seed(ticket_key, organization_name, owner_email, type_key, subtype_key, state_key,
          priority, severity, title, description, assigned_team, responsibility,
          target_user_email, target_user_snapshot, created_at, response_due_at,
          first_info_due_at, next_update_due_at)
JOIN organization ON organization.name = seed.organization_name
JOIN app_user owner ON owner.email = seed.owner_email
JOIN app_user lead ON lead.email = 'test.developer@ticketflow1.app'
JOIN ticket_type type ON type.organization_id = organization.id AND type.key = seed.type_key
JOIN workflow_state state ON state.workflow_id = type.workflow_id AND state.key = seed.state_key
LEFT JOIN ticket_subtype subtype ON subtype.ticket_type_id = type.id AND subtype.key = seed.subtype_key
LEFT JOIN app_user target_user ON target_user.email = seed.target_user_email
WHERE NOT EXISTS (SELECT 1 FROM ticket existing WHERE existing.ticket_key = seed.ticket_key);

INSERT INTO ticket_developer (ticket_id, user_id)
SELECT ticket.id, app_user.id
FROM ticket
JOIN app_user ON app_user.email IN ('test.developer@ticketflow1.app', 'test.manager@ticketflow1.app')
WHERE ticket.ticket_key IN ('TF-2000', 'TF-2001', 'TF-2002', 'TF-2003', 'TF-2004', 'TF-2005')
ON CONFLICT DO NOTHING;

INSERT INTO developer_team_ticket (team_id, ticket_id, sort_order)
SELECT team.id, ticket.id,
       row_number() OVER (PARTITION BY team.id ORDER BY ticket.ticket_key) - 1
FROM ticket
JOIN developer_team team ON team.name = ticket.assigned_team
WHERE ticket.ticket_key IN ('TF-2000', 'TF-2001', 'TF-2002', 'TF-2003', 'TF-2004', 'TF-2005')
  AND NOT EXISTS (
      SELECT 1 FROM developer_team_ticket existing
      WHERE existing.team_id = team.id AND existing.ticket_id = ticket.id
  );

INSERT INTO status_history (ticket_id, from_state_id, to_state_id, changed_by_id, created_at)
SELECT ticket.id, NULL, ticket.current_state_id, ticket.business_owner_id, ticket.created_at
FROM ticket
WHERE ticket.ticket_key IN ('TF-2000', 'TF-2001', 'TF-2002', 'TF-2003', 'TF-2004', 'TF-2005')
  AND NOT EXISTS (SELECT 1 FROM status_history existing WHERE existing.ticket_id = ticket.id);

INSERT INTO audit_log (ticket_id, actor_id, action, new_value, created_at)
SELECT ticket.id, ticket.business_owner_id, 'TICKET_CREATED', ticket.ticket_key, ticket.created_at
FROM ticket
WHERE ticket.ticket_key IN ('TF-2000', 'TF-2001', 'TF-2002', 'TF-2003', 'TF-2004', 'TF-2005')
  AND NOT EXISTS (
      SELECT 1 FROM audit_log existing
      WHERE existing.ticket_id = ticket.id AND existing.action = 'TICKET_CREATED'
  );

SELECT setval('ticket_key_seq', GREATEST((SELECT last_value FROM ticket_key_seq), 2005), true);
