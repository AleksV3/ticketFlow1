-- V31: repair tickets seeded directly into approval states after V26 ran.
-- Runtime transitions create ticket_approval in the service; additive seed
-- migrations must establish the same invariant for already-existing tickets.

INSERT INTO ticket_approval (
    ticket_id, pending_state_id, assigned_approver_id, assigned_team_id
)
SELECT ticket.id,
       ticket.current_state_id,
       COALESCE(ticket.resolved_approver_id, routing.approver_id),
       routing.team_id
FROM ticket
JOIN ticket_type type ON type.id = ticket.ticket_type_id
JOIN workflow_transition approve_edge
  ON approve_edge.workflow_id = type.workflow_id
 AND approve_edge.from_state_id = ticket.current_state_id
 AND approve_edge.operation_kind = 'WORKFLOW_APPROVE'
LEFT JOIN subtype_routing_rule routing
  ON routing.subtype_id = ticket.subtype_id
 AND routing.active
 AND routing.organization_id IS NOT DISTINCT FROM ticket.organization_id
WHERE (ticket.resolved_approver_id IS NOT NULL OR routing.team_id IS NOT NULL)
ON CONFLICT DO NOTHING;
