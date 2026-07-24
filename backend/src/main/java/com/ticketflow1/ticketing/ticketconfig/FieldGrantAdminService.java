package com.ticketflow1.ticketing.ticketconfig;
import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.rbac.Role;
import com.ticketflow1.ticketing.rbac.RoleRepository;
import com.ticketflow1.ticketing.configaudit.ConfigurationAuditService;
import com.ticketflow1.ticketing.ticket.Responsibility;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FieldGrantAdminService {
 private final SubtypeFieldDefinitionRepository fields; private final SubtypeFieldRoleGrantRepository grants; private final RoleRepository roles; private final ConfigurationAuditService audit;
 public FieldGrantAdminService(SubtypeFieldDefinitionRepository fields,SubtypeFieldRoleGrantRepository grants,RoleRepository roles,ConfigurationAuditService audit){this.fields=fields;this.grants=grants;this.roles=roles;this.audit=audit;}
 @Transactional public List<SubtypeFieldRoleGrant> replace(AuthPrincipal p,Long fieldId,Collection<Long> view,Collection<Long> edit,Collection<Long> create){
  if(p.party()!=Responsibility.TICKETFLOW1||!p.hasPermission("TYPE_MANAGE"))throw ApiException.forbidden("TYPE_MANAGE permission is required.");
  SubtypeFieldDefinition f=fields.findById(fieldId).orElseThrow(()->ApiException.notFound("Field not found: "+fieldId));
  Set<Long> ids=new LinkedHashSet<>(); if(view!=null)ids.addAll(view);if(edit!=null)ids.addAll(edit);if(create!=null)ids.addAll(create);
  List<Role> rs=roles.findAllById(ids);if(rs.size()!=ids.size()||rs.stream().anyMatch(r->r.getOrganization()!=null&&f.getSubtype().getTicketType().getOrganization()!=null&&!r.getOrganization().getId().equals(f.getSubtype().getTicketType().getOrganization().getId())))throw ApiException.validation("Role is outside field configuration scope.");
  boolean internal=f.getSubtype().getTicketType().getOrganization()==null;
  if(rs.stream().anyMatch(r->r.getParty()!= (internal ? Responsibility.TICKETFLOW1 : Responsibility.CLIENT))) throw ApiException.validation("Role party does not match field configuration scope.");
  grants.deleteAll(grants.findByFieldId(fieldId)); Map<Long,Role> map=new HashMap<>();rs.forEach(r->map.put(r.getId(),r)); List<SubtypeFieldRoleGrant> out=new ArrayList<>(); add(out,f,map,view,FieldGrantOperation.VIEW);add(out,f,map,edit,FieldGrantOperation.EDIT);add(out,f,map,create,FieldGrantOperation.CREATE); List<SubtypeFieldRoleGrant> saved=grants.saveAll(out);
  audit.record(f.getSubtype().getTicketType().getOrganization(),p.userId(),"SUBTYPE_FIELD",fieldId,"GRANTS_REPLACED",null,java.util.Map.of("view",new LinkedHashSet<>(view==null?List.of():view),"edit",new LinkedHashSet<>(edit==null?List.of():edit),"create",new LinkedHashSet<>(create==null?List.of():create)).toString());
  return saved;
 }
 private void add(List<SubtypeFieldRoleGrant> out,SubtypeFieldDefinition f,Map<Long,Role> m,Collection<Long> ids,FieldGrantOperation op){if(ids!=null)for(Long id:new LinkedHashSet<>(ids)){Role r=m.get(id);if(r!=null)out.add(new SubtypeFieldRoleGrant(f,r,op));}}
 @Transactional(readOnly=true) public Map<String,List<Long>> get(Long fieldId){Map<String,List<Long>> out=new LinkedHashMap<>();for(FieldGrantOperation op:FieldGrantOperation.values())out.put(op.name(),grants.findByFieldId(fieldId).stream().filter(g->g.getOperation()==op).map(g->g.getRole().getId()).toList());return out;}
}
