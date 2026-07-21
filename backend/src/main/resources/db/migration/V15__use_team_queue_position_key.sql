ALTER TABLE developer_team_ticket
    DROP CONSTRAINT developer_team_ticket_pkey;

ALTER TABLE developer_team_ticket
    ADD CONSTRAINT developer_team_ticket_pkey PRIMARY KEY (team_id, sort_order);
