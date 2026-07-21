package com.ticketflow1.ticketing.workflow;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.configaudit.ConfigurationAuditService;
import com.ticketflow1.ticketing.organization.*;
import com.ticketflow1.ticketing.rbac.*;
import com.ticketflow1.ticketing.ticket.Responsibility;
import com.ticketflow1.ticketing.ticket.TicketRepository;
import com.ticketflow1.ticketing.statushistory.StatusHistoryRepository;
import com.ticketflow1.ticketing.ticketconfig.TicketSubtypeRepository;
import com.ticketflow1.ticketing.workflow.dto.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the business rules behind the admin workflow API.
 *
 * The service owns the real behavior: organization scoping, graph validation,
 * optimistic locking, transition persistence, and safety checks for deleting
 * states or reassigning ticket types. The controller only passes authenticated
 * requests through to this layer.
 */
@Service
public class WorkflowAdminService {
    private final WorkflowRepository workflows; private final WorkflowStateRepository states;
    private final WorkflowTransitionRepository transitions; private final TicketTypeRepository types;
    private final OrganizationRepository organizations; private final PermissionRepository permissions;
    private final ConfigurationAuditService audit;
    private final TicketRepository tickets;
    private final StatusHistoryRepository statusHistory;
    private final TicketSubtypeRepository subtypes;
    public WorkflowAdminService(WorkflowRepository workflows, WorkflowStateRepository states,
            WorkflowTransitionRepository transitions, TicketTypeRepository types, OrganizationRepository organizations,
            PermissionRepository permissions, ConfigurationAuditService audit, TicketRepository tickets,
            StatusHistoryRepository statusHistory, TicketSubtypeRepository subtypes) {
        this.workflows=workflows; this.states=states; this.transitions=transitions; this.types=types;
        this.organizations=organizations; this.permissions=permissions; this.audit=audit; this.tickets=tickets; this.statusHistory=statusHistory; this.subtypes=subtypes;
    }
    /**
     * Returns workflows that the current user can access.
     */
    @Transactional(readOnly=true) public List<WorkflowResponse> listWorkflows(AuthPrincipal p, Long orgId) {
        Long scope=scope(p,orgId); List<Workflow> list=scope==null?workflows.findByOrganizationIsNull():workflows.findByOrganizationId(scope);
        return list.stream().map(this::response).toList();
    }
    /**
     * Creates a workflow, its states, and its standard transitions in one transaction.
     */
    @Transactional public WorkflowResponse createWorkflow(AuthPrincipal p, WorkflowRequests.Create r) {
        Organization org=optionalOrganization(p,r.organizationId()); validateGraph(r.states(),r.transitions());
        Workflow w=workflows.saveAndFlush(new Workflow(r.name(),org));
        Map<String,WorkflowState> map=r.states().stream().map(s->states.save(new WorkflowState(w,s.key(),s.isInitial(),s.isTerminal(),s.sortOrder())))
                .collect(Collectors.toMap(WorkflowState::getKey,Function.identity()));
        saveTransitions(w,map,r.transitions()); audit.record(org,p.userId(),"WORKFLOW",w.getId(),"CREATED",null,"{\"name\":\""+r.name()+"\"}");
        return response(w);
    }
    /**
     * Applies an admin update while preserving protected business transitions.
     */
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
    /**
     * Deletes a workflow state only when it is not the initial state and is not
     * already referenced by tickets or status history.
     */
    @Transactional public void removeState(AuthPrincipal p,Long workflowId,Long stateId){
        Workflow workflow=visible(p,workflowId);
        WorkflowState state=states.findById(stateId).filter(item->item.getWorkflow().getId().equals(workflow.getId()))
                .orElseThrow(()->ApiException.notFound("Workflow state not found: "+stateId));
        if(state.isInitial())throw ApiException.validation("The starting state cannot be removed.");
        if(tickets.existsByCurrentStateId(stateId)||statusHistory.existsByFromStateIdOrToStateId(stateId,stateId))
            throw ApiException.conflict("This state is already used by tickets and cannot be removed.");
        transitions.findByWorkflowId(workflowId).stream().filter(edge->edge.getFromState().getId().equals(stateId)||edge.getToState().getId().equals(stateId)).forEach(transitions::delete);
        states.delete(state);
    }
    /**
     * Lists ticket types that belong to the current scope.
     */
    @Transactional(readOnly=true) public List<TicketTypeAdminResponse> listTypes(AuthPrincipal p,Long orgId){Long s=scope(p,orgId);return (s==null?types.findByOrganizationIsNull():types.findByOrganizationId(s)).stream().map(TicketTypeAdminResponse::from).toList();}
    /**
     * Creates a ticket type and ensures it is compatible with the selected workflow.
     */
    @Transactional public TicketTypeAdminResponse createType(AuthPrincipal p,WorkflowRequests.CreateType r){
        requireInternal(p);if(r.requiresProposal())throw ApiException.validation("Custom ticket types cannot require proposals.");
        Organization org=optionalOrganization(p,r.organizationId());Workflow w=visible(p,r.workflowId());sameScope(w,org,r.workflowId());
        String key=typeKey(r.key());if((org==null?types.findByOrganizationIsNullAndKey(key):types.findByOrganizationIdAndKey(org.getId(),key)).isPresent())throw ApiException.validation("Duplicate ticket type key.");
        TicketType t=new TicketType(key,requiredName(r.name()),w,org,false,false);t.configure(requiredName(r.name()),w,r.active()==null||r.active(),order(r.sortOrder()),capability(r.capability()));types.saveAndFlush(t);
        audit.record(org,p.userId(),"TICKET_TYPE",t.getId(),"CREATED",null,"{\"key\":\""+key+"\"}");return TicketTypeAdminResponse.from(t);}
    /**
     * Repoints an existing ticket type to another workflow after validating scope
     * and ticket history constraints.
     */
    @Transactional public TicketTypeAdminResponse updateType(AuthPrincipal p,Long id,WorkflowRequests.UpdateType r){
        requireInternal(p);TicketType type=type(id);if(r.version()==null||r.version()!=type.getVersion())throw ApiException.conflict("Ticket type was modified by another user.");
        Workflow workflow=r.workflowId()==null?type.getWorkflow():visible(p,r.workflowId());sameScope(workflow,type.getOrganization(),workflow.getId());
        if(!workflow.getId().equals(type.getWorkflow().getId())&&tickets.existsByTicketTypeId(id))throw ApiException.conflict("This ticket type already has tickets. Its workflow cannot be changed because their current states belong to the existing workflow.");
        type.configure(r.name()==null?type.getName():requiredName(r.name()),workflow,r.active()==null?type.isActive():r.active(),r.sortOrder()==null?type.getSortOrder():order(r.sortOrder()),r.capability()==null?type.getCapability():capability(r.capability()));types.flush();
        audit.record(type.getOrganization(),p.userId(),"TICKET_TYPE",id,"UPDATED",null,"{\"workflowId\":"+workflow.getId()+"}");return TicketTypeAdminResponse.from(type);
    }
    @Transactional public TicketTypeAdminResponse setTypeActive(AuthPrincipal p,Long id,boolean active){requireInternal(p);TicketType type=type(id);type.setActive(active);types.flush();audit.record(type.getOrganization(),p.userId(),"TICKET_TYPE",id,active?"ACTIVATED":"DEACTIVATED",null,"{\"active\":"+active+"}");return TicketTypeAdminResponse.from(type);}
    @Transactional public void deleteType(AuthPrincipal p,Long id){requireInternal(p);TicketType type=type(id);if(tickets.existsByTicketTypeId(id)||subtypes.existsByTicketTypeId(id))throw ApiException.conflict("Ticket type is referenced and can only be deactivated.");types.delete(type);audit.record(type.getOrganization(),p.userId(),"TICKET_TYPE",id,"DELETED",null,"{\"key\":\""+type.getKey()+"\"}");}
    private void saveTransitions(Workflow w,Map<String,WorkflowState> map,List<WorkflowRequests.Transition> defs){for(var d:defs){if(d.operationKind()!=null&&d.operationKind()!=TransitionOperationKind.STANDARD)throw ApiException.validation("Custom workflows may use only STANDARD operations."); Permission perm=permissions.findByKey(d.requiredPermission()).orElseThrow(()->ApiException.validation("Unknown permission: "+d.requiredPermission()));transitions.save(new WorkflowTransition(w,map.get(d.fromState()),map.get(d.toState()),perm,d.requiredParty(),d.responsibilityAfter(),TransitionOperationKind.STANDARD));}}
    private void validateGraph(List<WorkflowRequests.State> s,List<WorkflowRequests.Transition> t){if(s==null||s.stream().filter(WorkflowRequests.State::isInitial).count()!=1)throw ApiException.validation("Exactly one initial state is required.");if(s.stream().noneMatch(WorkflowRequests.State::isTerminal))throw ApiException.validation("At least one terminal state is required.");Set<String> keys=s.stream().map(WorkflowRequests.State::key).collect(Collectors.toSet());if(keys.size()!=s.size())throw ApiException.validation("State keys must be unique.");if(t!=null&&t.stream().anyMatch(x->!keys.contains(x.fromState())||!keys.contains(x.toState())))throw ApiException.validation("Transitions must reference defined states.");}
    private Workflow visible(AuthPrincipal p,Long id){Workflow w=workflows.findById(id).orElseThrow(()->ApiException.notFound("Workflow not found: "+id));if(p.party()==Responsibility.CLIENT&&(w.getOrganization()==null||!w.getOrganization().getId().equals(p.organizationId())))throw ApiException.notFound("Workflow not found: "+id);return w;}
    private Long scope(AuthPrincipal p,Long requested){return p.party()==Responsibility.CLIENT?p.organizationId():requested;}
    private Organization organization(AuthPrincipal p,Long requested){if(p.party()==Responsibility.CLIENT&&requested!=null&&!p.organizationId().equals(requested))throw ApiException.notFound("Organization not found: "+requested);Long id=scope(p,requested);if(id==null)throw ApiException.validation("organizationId is required.");return organizations.findById(id).orElseThrow(()->ApiException.notFound("Organization not found: "+id));}
    private Organization optionalOrganization(AuthPrincipal p,Long requested){
        if(p.party()==Responsibility.CLIENT) return organization(p,requested);
        return requested==null?null:organizations.findById(requested).orElseThrow(()->ApiException.notFound("Organization not found: "+requested));
    }
    private TicketType type(Long id){return types.findById(id).orElseThrow(()->ApiException.notFound("Ticket type not found: "+id));}
    private void requireInternal(AuthPrincipal p){if(p.party()!=Responsibility.TICKETFLOW1)throw ApiException.forbidden("TICKETFLOW1 party is required.");}
    private void sameScope(Workflow workflow,Organization org,Long workflowId){Long workflowOrg=workflow.getOrganization()==null?null:workflow.getOrganization().getId();Long typeOrg=org==null?null:org.getId();if(!Objects.equals(workflowOrg,typeOrg))throw ApiException.notFound("Workflow not found: "+workflowId);}
    private String typeKey(String value){if(value==null||!value.trim().toUpperCase(Locale.ROOT).matches("[A-Z][A-Z0-9_]{1,39}"))throw ApiException.validation("Invalid ticket type key.");return value.trim().toUpperCase(Locale.ROOT);}
    private String requiredName(String value){if(value==null||value.isBlank()||value.trim().length()>100)throw ApiException.validation("Ticket type name must contain 1 to 100 characters.");return value.trim();}
    private int order(Integer value){int result=value==null?0:value;if(result<0)throw ApiException.validation("sortOrder cannot be negative.");return result;}
    private TicketTypeCapability capability(TicketTypeCapability value){return value==null?TicketTypeCapability.STANDARD:value;}
    private WorkflowResponse response(Workflow w){return WorkflowResponse.from(w,states.findByWorkflowIdOrderBySortOrderAsc(w.getId()),transitions.findByWorkflowId(w.getId()));}
}
