package com.ticketflow1.ticketing.ticketconfig;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.rbac.Role;
import com.ticketflow1.ticketing.user.AppUserRepository;
import java.util.*;
import org.springframework.stereotype.Service;

/** Shared server-side policy for dynamic field visibility and mutations. */
@Service
public class FieldAuthorizationService {
    private final SubtypeFieldRoleGrantRepository grants;
    private final AppUserRepository users;
    public FieldAuthorizationService(SubtypeFieldRoleGrantRepository grants, AppUserRepository users){this.grants=grants;this.users=users;}
    public boolean allowed(SubtypeFieldDefinition field, AuthPrincipal principal, FieldGrantOperation operation){
        List<SubtypeFieldRoleGrant> configured=grants.findByFieldId(field.getId());
        if(configured.isEmpty()) return principal.party()==com.ticketflow1.ticketing.ticket.Responsibility.TICKETFLOW1 || field.getVisibility()==FieldVisibility.PUBLIC;
        Set<Long> roleIds=users.findById(principal.userId()).map(u->u.getRoles().stream().map(Role::getId).collect(java.util.stream.Collectors.toSet())).orElse(Set.of());
        Long orgId=field.getSubtype().getTicketType().getOrganization()==null?null:field.getSubtype().getTicketType().getOrganization().getId();
        return configured.stream().anyMatch(g -> g.getOperation()==operation && roleIds.contains(g.getRole().getId()) &&
            (g.getRole().getOrganization()==null || Objects.equals(g.getRole().getOrganization().getId(),orgId)));
    }
    public void require(SubtypeFieldDefinition field, AuthPrincipal principal, FieldGrantOperation operation){
        if(!allowed(field,principal,operation)) throw ApiException.forbidden("Field '"+field.getKey()+"' does not permit "+operation+".");
    }
}
