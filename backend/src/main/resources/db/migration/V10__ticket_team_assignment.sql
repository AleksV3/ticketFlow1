INSERT INTO permission (key) VALUES ('TICKET_ASSIGN');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.organization_id IS NULL
  AND r.party = 'TICKETFLOW1'
  AND r.name IN ('Admin', 'TicketFlow1 Manager')
  AND p.key = 'TICKET_ASSIGN';

CREATE TABLE ticket_developer (
    ticket_id BIGINT NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user (id),
    PRIMARY KEY (ticket_id, user_id)
);
