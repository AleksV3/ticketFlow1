-- Feature 003 Phase 2: fixed, developer-owned global approval override.
--
-- The permission is granted to the seeded internal Admin role. Custom roles
-- may receive it through Role Administration; client roles never receive it by
-- default and the domain service additionally requires TICKETFLOW1 party.

INSERT INTO permission (key)
VALUES ('APPROVE_ALL_TICKETS')
ON CONFLICT (key) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM role
JOIN permission ON permission.key = 'APPROVE_ALL_TICKETS'
WHERE role.name = 'Admin'
  AND role.party = 'TICKETFLOW1'
  AND role.organization_id IS NULL
ON CONFLICT DO NOTHING;

