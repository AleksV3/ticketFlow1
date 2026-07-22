package com.ticketflow1.ticketing.reference;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.organization.OrganizationRepository;
import com.ticketflow1.ticketing.rbac.RoleRepository;
import com.ticketflow1.ticketing.ticket.Responsibility;
import com.ticketflow1.ticketing.ticketconfig.*;
import com.ticketflow1.ticketing.user.AppUserRepository;
import com.ticketflow1.ticketing.workflow.TicketTypeRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/reference")
public class ReferenceController {
    private static final java.util.Set<String> CLIENT_CREATABLE_TYPES = java.util.Set.of("DFCT", "REQ", "DEFECT", "REQUEST");
    private static final String INTERNAL_ORGANIZATION_NAME = "TicketFlow1 Internal";
    private final TicketTypeRepository types; private final OrganizationRepository organizations;
    private final AppUserRepository users; private final RoleRepository roles;
    private final TicketSubtypeRepository subtypes; private final SubtypeFieldDefinitionRepository fields;
    private final SubtypeFieldOptionRepository options;
    public ReferenceController(TicketTypeRepository types,OrganizationRepository organizations,
            AppUserRepository users,RoleRepository roles,TicketSubtypeRepository subtypes,
            SubtypeFieldDefinitionRepository fields,SubtypeFieldOptionRepository options){this.types=types;this.organizations=organizations;this.users=users;this.roles=roles;this.subtypes=subtypes;this.fields=fields;this.options=options;}
    @GetMapping("/ticket-types") @PreAuthorize("hasAuthority('TICKET_CREATE')")
    public List<TypeRef> types(@AuthenticationPrincipal AuthPrincipal p,@RequestParam(required=false)Long organizationId){Long id=p.party()==Responsibility.CLIENT?p.organizationId():organizationId;if(id==null){id=organizations.findByNameIgnoreCase(INTERNAL_ORGANIZATION_NAME).map(o->o.getId()).orElseThrow(()->ApiException.validation("organizationId is required."));}return types.findByOrganizationId(id).stream().filter(t->p.party()==Responsibility.TICKETFLOW1||CLIENT_CREATABLE_TYPES.contains(t.getKey())).map(t->new TypeRef(t.getId(),t.getKey(),t.getName())).toList();}
    @GetMapping("/ticket-types/{typeId}/creation-form") @PreAuthorize("hasAuthority('TICKET_CREATE')")
    public CreationForm creationForm(@AuthenticationPrincipal AuthPrincipal p,@PathVariable Long typeId){
        var type=types.findById(typeId).filter(t->t.isActive()&&authorized(p,t.getOrganization()==null?null:t.getOrganization().getId()))
                .orElseThrow(()->ApiException.notFound("Ticket type not found: "+typeId));
        List<SubtypeForm> subtypeForms=subtypes.findByTicketTypeIdAndActiveTrueOrderBySortOrderAscIdAsc(typeId).stream().map(s->{
            List<FieldForm> fieldForms=fields.findBySubtypeIdAndActiveTrueOrderBySortOrderAscIdAsc(s.getId()).stream()
                    .filter(f->p.party()==Responsibility.TICKETFLOW1||f.getVisibility()==FieldVisibility.PUBLIC)
                    .map(f->new FieldForm(f.getId(),f.getKey(),f.getLabel(),f.getHelpText(),f.getFieldKind(),f.isRequired(),f.getVisibility(),f.getSortOrder(),f.getMinLength(),f.getMaxLength(),f.getMinNumber(),f.getMaxNumber(),f.getVersion(),
                            options.findByFieldDefinitionIdAndActiveTrueOrderBySortOrderAscIdAsc(f.getId()).stream().map(o->new OptionForm(o.getId(),o.getKey(),o.getLabel(),o.getSortOrder(),o.getVersion())).toList())).toList();
            return new SubtypeForm(s.getId(),s.getKey(),s.getName(),s.getDescription(),s.getSortOrder(),s.getVersion(),fieldForms);
        }).toList();
        return new CreationForm(type.getId(),type.getKey(),type.getName(),type.getVersion(),subtypeForms);
    }
    @GetMapping("/organizations") @PreAuthorize("hasAuthority('TICKET_CREATE')")
    public List<IdName> organizations(@AuthenticationPrincipal AuthPrincipal p){requireInternal(p);return organizations.findByActiveTrueOrderByNameAsc().stream().map(o->new IdName(o.getId(),o.getName())).toList();}
    @GetMapping("/ticket-leads") @PreAuthorize("hasAuthority('TICKET_ASSIGN')")
    public List<IdName> leads(@AuthenticationPrincipal AuthPrincipal p){requireInternal(p);return users.findByActiveTrueAndPartyOrderByDisplayNameAsc(Responsibility.TICKETFLOW1).stream().map(u->new IdName(u.getId(),u.getDisplayName())).toList();}
    @GetMapping("/users") @PreAuthorize("hasAuthority('TICKET_CREATE')")
    public List<UserRef> targetUsers(@AuthenticationPrincipal AuthPrincipal p,@RequestParam String q,
            @RequestParam String purpose,@RequestParam(required=false) Long organizationId){
        if(!"USR_TARGET".equals(purpose))throw ApiException.validation("Unsupported user-search purpose.");
        String query=q==null?"":q.trim();if(query.length()<2)throw ApiException.validation("Search query must contain at least 2 characters.");
        Long scope=p.party()==Responsibility.CLIENT?p.organizationId():organizationId;
        if(scope==null)throw ApiException.validation("organizationId is required.");
        if(p.party()==Responsibility.CLIENT&&organizationId!=null&&!scope.equals(organizationId))throw ApiException.notFound("Organization not found: "+organizationId);
        var organization=organizations.findById(scope).filter(o->o.isActive()).orElseThrow(()->ApiException.notFound("Organization not found: "+scope));
        var results=p.party()==Responsibility.TICKETFLOW1&&INTERNAL_ORGANIZATION_NAME.equalsIgnoreCase(organization.getName())
                ? users.searchActiveDirectory(query,PageRequest.of(0,20))
                : users.searchActiveDirectory(scope,query,PageRequest.of(0,20));
        return results.stream().map(u->new UserRef(u.getId(),u.getDisplayName(),u.getEmail())).toList();
    }
    @GetMapping("/assignable-roles") @PreAuthorize("hasAuthority('USER_MANAGE')")
    public List<RoleRef> roles(@AuthenticationPrincipal AuthPrincipal p,@RequestParam(required=false)Long organizationId){Long id=p.party()==Responsibility.CLIENT?p.organizationId():organizationId;return (id==null?roles.findByOrganizationIsNull():roles.findByOrganizationId(id)).stream().map(r->new RoleRef(r.getId(),r.getName(),r.getParty().name())).toList();}
    private void requireInternal(AuthPrincipal p){if(p.party()!=Responsibility.TICKETFLOW1)throw ApiException.forbidden("TICKETFLOW1 party is required.");}
    private boolean authorized(AuthPrincipal p,Long organizationId){return p.party()==Responsibility.TICKETFLOW1||organizationId!=null&&organizationId.equals(p.organizationId());}
    public record IdName(Long id,String name){} public record TypeRef(Long id,String key,String name){} public record RoleRef(Long id,String name,String party){}
    public record UserRef(Long id,String displayName,String email){}
    public record CreationForm(Long id,String key,String name,long version,List<SubtypeForm> subtypes){}
    public record SubtypeForm(Long id,String key,String name,String description,int sortOrder,long version,List<FieldForm> fields){}
    public record FieldForm(Long id,String key,String label,String helpText,FieldKind fieldKind,boolean required,
            FieldVisibility visibility,int sortOrder,Integer minLength,Integer maxLength,java.math.BigDecimal minNumber,
            java.math.BigDecimal maxNumber,long version,List<OptionForm> options){}
    public record OptionForm(Long id,String key,String label,int sortOrder,long version){}
}
