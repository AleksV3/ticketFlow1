-- COMMENT_INTERNAL_READ was documented from the start but omitted from V1.
-- Add it forward-only because deployed Flyway migrations must never be edited.
INSERT INTO permission (key)
VALUES ('COMMENT_INTERNAL_READ')
ON CONFLICT (key) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.key = 'COMMENT_INTERNAL_READ'
WHERE r.party = 'TICKETFLOW1'
  AND r.name IN ('Admin', 'TicketFlow1 User', 'TicketFlow1 Manager')
ON CONFLICT DO NOTHING;
