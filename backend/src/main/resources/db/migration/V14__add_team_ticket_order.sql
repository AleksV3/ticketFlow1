ALTER TABLE developer_team_ticket
    ADD COLUMN sort_order INTEGER;

WITH ranked AS (
    SELECT team_id, ticket_id,
           ROW_NUMBER() OVER (PARTITION BY team_id ORDER BY ticket_id) - 1 AS position
    FROM developer_team_ticket
)
UPDATE developer_team_ticket relation
SET sort_order = ranked.position
FROM ranked
WHERE relation.team_id = ranked.team_id
  AND relation.ticket_id = ranked.ticket_id;

ALTER TABLE developer_team_ticket
    ALTER COLUMN sort_order SET NOT NULL;
