package com.ticketflow1.ticketing.reference;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.organization.OrganizationRepository;
import com.ticketflow1.ticketing.rbac.RoleRepository;
import com.ticketflow1.ticketing.ticket.Responsibility;
import com.ticketflow1.ticketing.user.AppUserRepository;
import com.ticketflow1.ticketing.workflow.TicketTypeRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/reference")
public class ReferenceController {
    private final TicketTypeRepository types; private final OrganizationRepository organizations;
    private final AppUserRepository users; private final RoleRepository roles;
    public ReferenceController(TicketTypeRepository types,OrganizationRepository organizations,
            AppUserRepository users,RoleRepository roles){this.types=types;this.organizations=organizations;this.users=users;this.roles=roles;}
    @GetMapping("/ticket-types") @PreAuthorize("hasAuthority('TICKET_CREATE')")
    public List<TypeRef> types(@AuthenticationPrincipal AuthPrincipal p,@RequestParam(required=false)Long organizationId){Long id=p.party()==Responsibility.CLIENT?p.organizationId():organizationId;if(id==null)throw ApiException.validation("organizationId is required.");return types.findByOrganizationId(id).stream().map(t->new TypeRef(t.getId(),t.getKey(),t.getName())).toList();}
    @GetMapping("/organizations") @PreAuthorize("hasAuthority('TICKET_CREATE')")
    public List<IdName> organizations(@AuthenticationPrincipal AuthPrincipal p){requireInternal(p);return organizations.findByActiveTrueOrderByNameAsc().stream().map(o->new IdName(o.getId(),o.getName())).toList();}
    @GetMapping("/ticket-leads") @PreAuthorize("hasAuthority('TICKET_UPDATE')")
    public List<IdName> leads(@AuthenticationPrincipal AuthPrincipal p){requireInternal(p);return users.findByActiveTrueAndPartyOrderByDisplayNameAsc(Responsibility.TICKETFLOW1).stream().map(u->new IdName(u.getId(),u.getDisplayName())).toList();}
    @GetMapping("/assignable-roles") @PreAuthorize("hasAuthority('USER_MANAGE')")
    public List<RoleRef> roles(@AuthenticationPrincipal AuthPrincipal p,@RequestParam(required=false)Long organizationId){Long id=p.party()==Responsibility.CLIENT?p.organizationId():organizationId;return (id==null?roles.findByOrganizationIsNull():roles.findByOrganizationId(id)).stream().map(r->new RoleRef(r.getId(),r.getName(),r.getParty().name())).toList();}
    private void requireInternal(AuthPrincipal p){if(p.party()!=Responsibility.TICKETFLOW1)throw ApiException.forbidden("TICKETFLOW1 party is required.");}
    public record IdName(Long id,String name){} public record TypeRef(Long id,String key,String name){} public record RoleRef(Long id,String name,String party){}
}
