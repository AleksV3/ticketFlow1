package com.ticketflow1.ticketing.workflow;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.configaudit.ConfigurationAuditService;
import com.ticketflow1.ticketing.organization.*;
import com.ticketflow1.ticketing.rbac.*;
import com.ticketflow1.ticketing.ticket.Responsibility;
import com.ticketflow1.ticketing.ticket.TicketRepository;
import com.ticketflow1.ticketing.workflow.dto.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowAdminService {
    private final WorkflowRepository workflows; private final WorkflowStateRepository states;
    private final WorkflowTransitionRepository transitions; private final TicketTypeRepository types;
    private final OrganizationRepository organizations; private final PermissionRepository permissions;
    private final ConfigurationAuditService audit;
    private final TicketRepository tickets;
    public WorkflowAdminService(WorkflowRepository workflows, WorkflowStateRepository states,
            WorkflowTransitionRepository transitions, TicketTypeRepository types, OrganizationRepository organizations,
            PermissionRepository permissions, ConfigurationAuditService audit, TicketRepository tickets) {
        this.workflows=workflows; this.states=states; this.transitions=transitions; this.types=types;
        this.organizations=organizations; this.permissions=permissions; this.audit=audit; this.tickets=tickets;
    }
    @Transactional(readOnly=true) public List<WorkflowResponse> listWorkflows(AuthPrincipal p, Long orgId) {
        Long scope=scope(p,orgId); List<Workflow> list=scope==null?workflows.findByOrganizationIsNull():workflows.findByOrganizationId(scope);
        return list.stream().map(this::response).toList();
    }
    @Transactional public WorkflowResponse createWorkflow(AuthPrincipal p, WorkflowRequests.Create r) {
        Organization org=organization(p,r.organizationId()); validateGraph(r.states(),r.transitions());
        Workflow w=workflows.saveAndFlush(new Workflow(r.name(),org));
        Map<String,WorkflowState> map=r.states().stream().map(s->states.save(new WorkflowState(w,s.key(),s.isInitial(),s.isTerminal(),s.sortOrder())))
                .collect(Collectors.toMap(WorkflowState::getKey,Function.identity()));
        saveTransitions(w,map,r.transitions()); audit.record(org,p.userId(),"WORKFLOW",w.getId(),"CREATED",null,"{\"name\":\""+r.name()+"\"}");
        return response(w);
    }
    @Transactional public WorkflowResponse updateWorkflow(AuthPrincipal p,Long id,WorkflowRequests.Update r) {
        Workflow w=visible(p,id); if(r.version()==null||r.version()!=w.getVersion()) throw ApiException.conflict("Workflow was modified by another user.");
        Map<String,WorkflowState> map=states.findByWorkflowIdOrderBySortOrderAsc(id).stream().collect(Collectors.toMap(WorkflowState::getKey,Function.identity()));
        if(r.states()!=null) for(var s:r.states()) {
            WorkflowState existing=map.get(s.key());
            if(existing==null) map.put(s.key(),states.save(new WorkflowState(w,s.key(),s.isInitial(),s.isTerminal(),s.sortOrder())));
            else existing.reorder(s.sortOrder());
        }
        if(r.states()!=null) states.flush();
        List<WorkflowRequests.State> resulting=map.values().stream().map(s->new WorkflowRequests.State(s.getKey(),s.isInitial(),s.isTerminal(),s.getSortOrder())).toList();
        List<WorkflowRequests.Transition> defs=r.transitions()==null?List.of():r.transitions(); validateGraph(resulting,defs);
        if(r.transitions()!=null){
            // Admins replace the editable STANDARD graph edges. Proposal
            // approve/reject/create edges are protected business commands and
            // remain intact while ordinary branches are redesigned.
            transitions.findByWorkflowId(id).stream()
                    .filter(edge -> edge.getOperationKind() == TransitionOperationKind.STANDARD)
                    .forEach(transitions::delete);
            transitions.flush();
            saveTransitions(w,map,r.transitions());
        }
        w.touchForAudit(); workflows.flush(); audit.record(w.getOrganization(),p.userId(),"WORKFLOW",id,"UPDATED",null,"{\"version\":"+w.getVersion()+"}"); return response(w);
    }
    @Transactional(readOnly=true) public List<TicketTypeAdminResponse> listTypes(AuthPrincipal p,Long orgId){Long s=scope(p,orgId);return (s==null?types.findByOrganizationIsNull():types.findByOrganizationId(s)).stream().map(TicketTypeAdminResponse::from).toList();}
    @Transactional public TicketTypeAdminResponse createType(AuthPrincipal p,WorkflowRequests.CreateType r){if(r.requiresProposal())throw ApiException.validation("Custom ticket types cannot require proposals."); Organization org=organization(p,r.organizationId()); Workflow w=visible(p,r.workflowId()); if(w.getOrganization()!=null&&!w.getOrganization().getId().equals(org.getId()))throw ApiException.notFound("Workflow not found: "+r.workflowId()); if(types.findByOrganizationIdAndKey(org.getId(),r.key()).isPresent())throw ApiException.validation("Duplicate ticket type key."); TicketType t=types.saveAndFlush(new TicketType(r.key(),r.name(),w,org,false,false));audit.record(org,p.userId(),"TICKET_TYPE",t.getId(),"CREATED",null,"{\"key\":\""+r.key()+"\"}");return TicketTypeAdminResponse.from(t);}
    @Transactional public TicketTypeAdminResponse updateType(AuthPrincipal p,Long id,WorkflowRequests.UpdateType r){
        TicketType type=types.findById(id).orElseThrow(()->ApiException.notFound("Ticket type not found: "+id));
        if(type.getOrganization()==null||(p.party()==Responsibility.CLIENT&&!type.getOrganization().getId().equals(p.organizationId())))throw ApiException.notFound("Ticket type not found: "+id);
        Workflow workflow=visible(p,r.workflowId());
        if(workflow.getOrganization()==null||!workflow.getOrganization().getId().equals(type.getOrganization().getId()))throw ApiException.notFound("Workflow not found: "+r.workflowId());
        if(tickets.existsByTicketTypeId(id))throw ApiException.conflict("This ticket type already has tickets. Its workflow cannot be changed because their current states belong to the existing workflow.");
        type.applyWorkflow(workflow); types.flush(); audit.record(type.getOrganization(),p.userId(),"TICKET_TYPE",id,"UPDATED",null,"{\"workflowId\":"+workflow.getId()+"}");return TicketTypeAdminResponse.from(type);
    }
    private void saveTransitions(Workflow w,Map<String,WorkflowState> map,List<WorkflowRequests.Transition> defs){for(var d:defs){if(d.operationKind()!=null&&d.operationKind()!=TransitionOperationKind.STANDARD)throw ApiException.validation("Custom workflows may use only STANDARD operations."); Permission perm=permissions.findByKey(d.requiredPermission()).orElseThrow(()->ApiException.validation("Unknown permission: "+d.requiredPermission()));transitions.save(new WorkflowTransition(w,map.get(d.fromState()),map.get(d.toState()),perm,d.requiredParty(),d.responsibilityAfter(),TransitionOperationKind.STANDARD));}}
    private void validateGraph(List<WorkflowRequests.State> s,List<WorkflowRequests.Transition> t){if(s==null||s.stream().filter(WorkflowRequests.State::isInitial).count()!=1)throw ApiException.validation("Exactly one initial state is required.");if(s.stream().noneMatch(WorkflowRequests.State::isTerminal))throw ApiException.validation("At least one terminal state is required.");Set<String> keys=s.stream().map(WorkflowRequests.State::key).collect(Collectors.toSet());if(keys.size()!=s.size())throw ApiException.validation("State keys must be unique.");if(t!=null&&t.stream().anyMatch(x->!keys.contains(x.fromState())||!keys.contains(x.toState())))throw ApiException.validation("Transitions must reference defined states.");}
    private Workflow visible(AuthPrincipal p,Long id){Workflow w=workflows.findById(id).orElseThrow(()->ApiException.notFound("Workflow not found: "+id));if(p.party()==Responsibility.CLIENT&&(w.getOrganization()==null||!w.getOrganization().getId().equals(p.organizationId())))throw ApiException.notFound("Workflow not found: "+id);return w;}
    private Long scope(AuthPrincipal p,Long requested){return p.party()==Responsibility.CLIENT?p.organizationId():requested;}
    private Organization organization(AuthPrincipal p,Long requested){if(p.party()==Responsibility.CLIENT&&requested!=null&&!p.organizationId().equals(requested))throw ApiException.notFound("Organization not found: "+requested);Long id=scope(p,requested);if(id==null)throw ApiException.validation("organizationId is required.");return organizations.findById(id).orElseThrow(()->ApiException.notFound("Organization not found: "+id));}
    private WorkflowResponse response(Workflow w){return WorkflowResponse.from(w,states.findByWorkflowIdOrderBySortOrderAsc(w.getId()),transitions.findByWorkflowId(w.getId()));}
}
