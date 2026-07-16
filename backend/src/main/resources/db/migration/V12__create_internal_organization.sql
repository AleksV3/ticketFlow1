INSERT INTO organization (name)
SELECT 'TicketFlow1 Internal'
WHERE NOT EXISTS (SELECT 1 FROM organization WHERE lower(name) = lower('TicketFlow1 Internal'));

SELECT clone_org_templates(id) FROM organization
WHERE lower(name) = lower('TicketFlow1 Internal');
