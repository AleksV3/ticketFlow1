-- Approved service-request workflow templates.  These are templates so the
-- existing clone_org_templates function creates organization-owned copies.
ALTER TABLE workflow_transition DROP CONSTRAINT IF EXISTS workflow_transition_operation_kind_check;
ALTER TABLE workflow_transition ADD CONSTRAINT workflow_transition_operation_kind_check
    CHECK (operation_kind IN ('STANDARD','PROPOSAL_CREATE','PROPOSAL_APPROVE','PROPOSAL_REJECT',
                              'WORKFLOW_APPROVE','WORKFLOW_REJECT','CORRECTION_RETURN','CLIENT_ACCEPT','CLIENT_REJECT'));
ALTER TABLE audit_log DROP CONSTRAINT IF EXISTS audit_log_action_check;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_action_check CHECK (action IN (
    'TICKET_CREATED','STATUS_CHANGED','ASSIGNEE_CHANGED','COMMENT_ADDED','PROPOSAL_CREATED',
    'PROPOSAL_APPROVED','PROPOSAL_REJECTED','SEVERITY_CHANGED','PRIORITY_CHANGED',
    'ATTACHMENT_ADDED','DYNAMIC_FIELDS_CAPTURED','CORRECTION_RETURN','TICKET_UPDATED','CONFIG_CHANGED'));

INSERT INTO workflow(name, organization_id)
SELECT x.name, NULL FROM (VALUES ('TASI Workflow'),('USR Workflow'),('REQ Workflow')) x(name)
WHERE NOT EXISTS (SELECT 1 FROM workflow w WHERE w.name=x.name AND w.organization_id IS NULL);

INSERT INTO workflow_state(workflow_id,key,is_initial,is_terminal,sort_order)
SELECT w.id,s.key,s.initial_state,s.terminal_state,s.sort_order
FROM workflow w JOIN (VALUES
 ('TASI Workflow','NEW',true,false,10),('TASI Workflow','ANALYSIS',false,false,20),('TASI Workflow','PENDING_APPROVAL',false,false,30),('TASI Workflow','IMPLEMENTATION',false,false,40),('TASI Workflow','CLOSED',false,true,50),
 ('USR Workflow','NEW',true,false,10),('USR Workflow','ANALYSIS',false,false,20),('USR Workflow','PENDING_APPROVAL',false,false,30),('USR Workflow','IMPLEMENTATION',false,false,40),('USR Workflow','CLOSED',false,true,50),
 ('REQ Workflow','SUBMITTED',true,false,10),('REQ Workflow','ANALYSIS',false,false,20),('REQ Workflow','CLIENT_ACCEPTANCE',false,false,30),('REQ Workflow','DEPLOYMENT',false,false,40),('REQ Workflow','CLOSED',false,true,50)
) s(workflow_name,key,initial_state,terminal_state,sort_order) ON s.workflow_name=w.name
WHERE NOT EXISTS (SELECT 1 FROM workflow_state e WHERE e.workflow_id=w.id AND e.key=s.key);

INSERT INTO workflow_transition(workflow_id,from_state_id,to_state_id,required_permission_id,required_party,responsibility_after,operation_kind)
SELECT w.id,fs.id,ts.id,p.id,t.required_party,t.responsibility_after,t.operation_kind
FROM (VALUES
 ('TASI Workflow','NEW','ANALYSIS','TICKET_TRANSITION','TICKETFLOW1',NULL,'STANDARD'),('TASI Workflow','ANALYSIS','NEW','TICKET_TRANSITION','TICKETFLOW1',NULL,'CORRECTION_RETURN'),('TASI Workflow','ANALYSIS','PENDING_APPROVAL','TICKET_TRANSITION','TICKETFLOW1',NULL,'STANDARD'),('TASI Workflow','PENDING_APPROVAL','IMPLEMENTATION','TICKET_TRANSITION','TICKETFLOW1',NULL,'WORKFLOW_APPROVE'),('TASI Workflow','PENDING_APPROVAL','ANALYSIS','TICKET_TRANSITION','TICKETFLOW1',NULL,'WORKFLOW_REJECT'),('TASI Workflow','IMPLEMENTATION','CLOSED','TICKET_TRANSITION','TICKETFLOW1',NULL,'STANDARD'),
 ('USR Workflow','NEW','ANALYSIS','TICKET_TRANSITION','TICKETFLOW1',NULL,'STANDARD'),('USR Workflow','ANALYSIS','NEW','TICKET_TRANSITION','TICKETFLOW1',NULL,'CORRECTION_RETURN'),('USR Workflow','ANALYSIS','PENDING_APPROVAL','TICKET_TRANSITION','TICKETFLOW1',NULL,'STANDARD'),('USR Workflow','PENDING_APPROVAL','IMPLEMENTATION','TICKET_TRANSITION','TICKETFLOW1',NULL,'WORKFLOW_APPROVE'),('USR Workflow','PENDING_APPROVAL','ANALYSIS','TICKET_TRANSITION','TICKETFLOW1',NULL,'WORKFLOW_REJECT'),('USR Workflow','IMPLEMENTATION','CLOSED','TICKET_TRANSITION','TICKETFLOW1',NULL,'STANDARD'),
 ('REQ Workflow','SUBMITTED','ANALYSIS','TICKET_TRANSITION','TICKETFLOW1',NULL,'STANDARD'),('REQ Workflow','ANALYSIS','SUBMITTED','TICKET_TRANSITION','TICKETFLOW1',NULL,'CORRECTION_RETURN'),('REQ Workflow','ANALYSIS','CLIENT_ACCEPTANCE','TICKET_TRANSITION','TICKETFLOW1','CLIENT','STANDARD'),('REQ Workflow','CLIENT_ACCEPTANCE','DEPLOYMENT','TICKET_TRANSITION','CLIENT','TICKETFLOW1','CLIENT_ACCEPT'),('REQ Workflow','CLIENT_ACCEPTANCE','ANALYSIS','TICKET_TRANSITION','CLIENT','TICKETFLOW1','CLIENT_REJECT'),('REQ Workflow','DEPLOYMENT','ANALYSIS','TICKET_TRANSITION','TICKETFLOW1',NULL,'CORRECTION_RETURN'),('REQ Workflow','DEPLOYMENT','CLOSED','TICKET_TRANSITION','TICKETFLOW1',NULL,'STANDARD')
) t(workflow_name,from_key,to_key,permission_key,required_party,responsibility_after,operation_kind)
JOIN workflow w ON w.name=t.workflow_name AND w.organization_id IS NULL
JOIN workflow_state fs ON fs.workflow_id=w.id AND fs.key=t.from_key
JOIN workflow_state ts ON ts.workflow_id=w.id AND ts.key=t.to_key
JOIN permission p ON p.key=t.permission_key
WHERE NOT EXISTS (SELECT 1 FROM workflow_transition e WHERE e.workflow_id=w.id AND e.from_state_id=fs.id AND e.to_state_id=ts.id);

INSERT INTO ticket_type(key,name,workflow_id,organization_id,is_template,requires_proposal)
SELECT x.key,x.name,w.id,NULL,true,false FROM (VALUES
 ('TASI','Technical Service Action','TASI Workflow'),('USR','User Service Request','USR Workflow'),('DFCT','Defect','Defect Workflow'),('REQ','Request','REQ Workflow')
) x(key,name,workflow_name) JOIN workflow w ON w.name=x.workflow_name AND w.organization_id IS NULL
WHERE NOT EXISTS (SELECT 1 FROM ticket_type t WHERE t.organization_id IS NULL AND t.key=x.key);

SELECT clone_org_templates(id) FROM organization;
