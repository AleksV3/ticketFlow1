-- Demo-only data. This location is loaded exclusively by application-demo.yml.
-- Every seeded account uses the password "admin123". Never add this location
-- to the default/production Flyway configuration.

INSERT INTO organization (name) VALUES
    ('Alpine Retail'),
    ('Coastal Logistics');

SELECT clone_org_templates(id)
FROM organization
WHERE name IN ('Alpine Retail', 'Coastal Logistics');

-- One account for each seeded role plus a second-organization contributor.
-- BCrypt hash is for the deliberately demo-only password documented above.
INSERT INTO app_user (email, password_hash, display_name, party, role_id, organization_id)
SELECT account.email, '$2a$10$ssUxK.6FCVlYURe4987adu/r5Ccy4B0rSfDOCsaiQO/rswNRW/Qje',
       account.display_name, account.party, r.id, o.id
FROM (VALUES
    ('admin@ticketflow1.demo', 'TicketFlow1 Admin', 'TICKETFLOW1', 'Admin', NULL),
    ('agent@ticketflow1.demo', 'Maya Agent', 'TICKETFLOW1', 'TicketFlow1 User', NULL),
    ('manager@ticketflow1.demo', 'Noah Manager', 'TICKETFLOW1', 'TicketFlow1 Manager', NULL),
    ('contributor@alpine.demo', 'Ava Contributor', 'CLIENT', 'Client User', 'Alpine Retail'),
    ('approver@alpine.demo', 'Liam Approver', 'CLIENT', 'Client Approver', 'Alpine Retail'),
    ('contributor@coastal.demo', 'Sofia Contributor', 'CLIENT', 'Client User', 'Coastal Logistics')
) AS account(email, display_name, party, role_name, organization_name)
LEFT JOIN organization o ON o.name = account.organization_name
JOIN role r ON r.name = account.role_name
 AND r.party = account.party
 AND r.organization_id IS NOT DISTINCT FROM o.id;

-- Realistic records make dashboard, isolation, workflow and SLA behavior
-- visible immediately while leaving the main demo Change Request to be
-- created live.
INSERT INTO ticket (
    ticket_key, ticket_type_id, current_state_id, priority, severity,
    title, description, organization_id, business_owner_id, ticket_lead_id,
    assigned_team, current_responsibility, created_at,
    response_due_at, first_info_due_at, next_update_due_at
)
SELECT seed.ticket_key, tt.id, ws.id, seed.priority, seed.severity,
       seed.title, seed.description, o.id, owner.id, lead.id,
       seed.assigned_team, seed.responsibility, seed.created_at,
       seed.response_due_at, seed.first_info_due_at, seed.next_update_due_at
FROM (VALUES
    ('TF-1000', 'Alpine Retail', 'contributor@alpine.demo', 'DEFECT', 'ANALYSIS',
     'CRITICAL', 'SEV_1', 'Checkout payments intermittently fail',
     'Payment authorization returns a timeout for a subset of orders.',
     'Payments', 'TICKETFLOW1', now() - interval '30 minutes',
     now() - interval '15 minutes', now() + interval '90 minutes', now() + interval '30 minutes'),
    ('TF-1001', 'Alpine Retail', 'contributor@alpine.demo', 'TASK', 'DEVELOPMENT',
     'MEDIUM', NULL, 'Update monthly sales export',
     'Include the regional tax code in the finance export.',
     'Data Platform', 'TICKETFLOW1', now() - interval '3 days', NULL, NULL, NULL),
    ('TF-1002', 'Coastal Logistics', 'contributor@coastal.demo', 'DEFECT', 'REPORTED',
     'HIGH', 'SEV_2', 'Shipment labels omit the depot code',
     'PDF labels generated for international shipments miss the depot code.',
     'Fulfilment', 'TICKETFLOW1', now() - interval '1 hour',
     now() + interval '3 hours', now() + interval '7 hours', now() + interval '3 hours')
) AS seed(ticket_key, organization_name, owner_email, type_key, state_key,
          priority, severity, title, description, assigned_team, responsibility,
          created_at, response_due_at, first_info_due_at, next_update_due_at)
JOIN organization o ON o.name = seed.organization_name
JOIN app_user owner ON owner.email = seed.owner_email
JOIN app_user lead ON lead.email = 'agent@ticketflow1.demo'
JOIN ticket_type tt ON tt.organization_id = o.id AND tt.key = seed.type_key
JOIN workflow_state ws ON ws.workflow_id = tt.workflow_id AND ws.key = seed.state_key;

INSERT INTO status_history (ticket_id, from_state_id, to_state_id, changed_by_id, created_at)
SELECT t.id, NULL, t.current_state_id, t.business_owner_id, t.created_at
FROM ticket t
WHERE t.ticket_key IN ('TF-1000', 'TF-1001', 'TF-1002');

INSERT INTO audit_log (ticket_id, actor_id, action, new_value, created_at)
SELECT t.id, t.business_owner_id, 'TICKET_CREATED', t.ticket_key, t.created_at
FROM ticket t
WHERE t.ticket_key IN ('TF-1000', 'TF-1001', 'TF-1002');

SELECT setval('ticket_key_seq', 1002, true);
