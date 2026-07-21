package com.ticketflow1.ticketing.ticketconfig;

import com.fasterxml.jackson.core.JsonProcessingException; import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow1.ticketing.auth.AuthPrincipal; import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.configaudit.ConfigurationAuditService; import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.organization.OrganizationRepository; import com.ticketflow1.ticketing.team.DeveloperTeam;
import com.ticketflow1.ticketing.team.DeveloperTeamRepository; import com.ticketflow1.ticketing.user.AppUser;
import com.ticketflow1.ticketing.user.AppUserRepository;
import com.ticketflow1.ticketing.ticket.Responsibility; import com.ticketflow1.ticketing.workflow.TicketType;
import com.ticketflow1.ticketing.workflow.TicketTypeRepository; import jakarta.persistence.EntityManager;
import java.math.BigDecimal; import java.util.*; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketConfigurationService {
    private final TicketSubtypeRepository subtypes; private final SubtypeFieldDefinitionRepository fields;
    private final SubtypeFieldOptionRepository options; private final TicketTypeRepository types;
    private final SubtypeRoutingRuleRepository routing; private final DeveloperTeamRepository teams;
    private final AppUserRepository users; private final OrganizationRepository organizations;
    private final ConfigurationAuditService audit; private final EntityManager entityManager; private final ObjectMapper json;
    public TicketConfigurationService(TicketSubtypeRepository subtypes,SubtypeFieldDefinitionRepository fields,
            SubtypeFieldOptionRepository options,TicketTypeRepository types,SubtypeRoutingRuleRepository routing,
            DeveloperTeamRepository teams,AppUserRepository users,OrganizationRepository organizations,
            ConfigurationAuditService audit,EntityManager entityManager,ObjectMapper json){this.subtypes=subtypes;this.fields=fields;this.options=options;
        this.types=types;this.routing=routing;this.teams=teams;this.users=users;this.organizations=organizations;this.audit=audit;this.entityManager=entityManager;this.json=json;}

    @Transactional(readOnly=true) public List<TicketSubtype> listSubtypes(AuthPrincipal p,Long typeId){requireManage(p);type(typeId);return subtypes.findByTicketTypeIdOrderBySortOrderAscIdAsc(typeId);}

    @Transactional public TicketSubtype createSubtype(AuthPrincipal p,Long typeId,String key,String name,String description,int order){
        requireManage(p);TicketType type=type(typeId);key=upperKey(key);if(subtypes.findByTicketTypeIdAndKey(typeId,key).isPresent())throw ApiException.validation("Subtype key already exists.");
        TicketSubtype saved=subtypes.saveAndFlush(new TicketSubtype(type,key,required(name,"name",120),text(description,1000),order(order)));
        record(p,type.getOrganization(),"TICKET_SUBTYPE",saved.getId(),"CREATED",Map.of("key",key));return saved;}
    @Transactional public TicketSubtype updateSubtype(AuthPrincipal p,Long id,Long version,String name,String description,int order){requireManage(p);TicketSubtype s=subtype(id);version(version,s.getVersion());s.update(required(name,"name",120),text(description,1000),order(order));subtypes.flush();record(p,organization(s),"TICKET_SUBTYPE",id,"UPDATED",Map.of("version",s.getVersion()));return s;}
    @Transactional(readOnly=true) public List<SubtypeFieldDefinition> listFields(AuthPrincipal p,Long subtypeId){requireManage(p);subtype(subtypeId);return fields.findBySubtypeIdOrderBySortOrderAscIdAsc(subtypeId);}
    @Transactional public SubtypeFieldDefinition createField(AuthPrincipal p,Long subtypeId,String key,String label,String help,FieldKind kind,
            boolean required,FieldVisibility visibility,int order,Integer minLength,Integer maxLength,BigDecimal minNumber,BigDecimal maxNumber){
        requireManage(p);TicketSubtype subtype=subtype(subtypeId);if(fields.findBySubtypeIdOrderBySortOrderAscIdAsc(subtypeId).stream().filter(SubtypeFieldDefinition::isActive).count()>=50)throw ApiException.validation("A subtype may have at most 50 active fields.");key=lowerKey(key);if(fields.findBySubtypeIdAndKey(subtypeId,key).isPresent())throw ApiException.validation("Field key already exists.");
        validateBounds(kind,minLength,maxLength,minNumber,maxNumber);SubtypeFieldDefinition field=new SubtypeFieldDefinition(subtype,key,required(label,"label",120),text(help,1000),kind,required,visibility,order(order));
        field.setBounds(minLength,maxLength,minNumber,maxNumber);field=fields.saveAndFlush(field);record(p,organization(subtype),"SUBTYPE_FIELD",field.getId(),"CREATED",Map.of("key",key,"kind",kind));return field;}
    @Transactional public SubtypeFieldDefinition updateField(AuthPrincipal p,Long id,Long version,String label,String help,boolean required,FieldVisibility visibility,int order,Integer minLength,Integer maxLength,BigDecimal minNumber,BigDecimal maxNumber){requireManage(p);SubtypeFieldDefinition f=field(id);version(version,f.getVersion());validateBounds(f.getFieldKind(),minLength,maxLength,minNumber,maxNumber);if(visibility==null)throw ApiException.validation("visibility is required.");f.update(required(label,"label",120),text(help,1000),required,visibility,order(order));f.setBounds(minLength,maxLength,minNumber,maxNumber);fields.flush();record(p,organization(f.getSubtype()),"SUBTYPE_FIELD",id,"UPDATED",Map.of("version",f.getVersion()));return f;}
    @Transactional(readOnly=true) public List<SubtypeFieldOption> listOptions(AuthPrincipal p,Long fieldId){requireManage(p);field(fieldId);return options.findByFieldDefinitionIdOrderBySortOrderAscIdAsc(fieldId);}
    @Transactional public SubtypeFieldOption createOption(AuthPrincipal p,Long fieldId,String key,String label,int order){
        requireManage(p);SubtypeFieldDefinition field=field(fieldId);if(field.getFieldKind()!=FieldKind.SINGLE_SELECT&&field.getFieldKind()!=FieldKind.MULTI_SELECT)throw ApiException.validation("Options are allowed only for select fields.");if(options.findByFieldDefinitionIdOrderBySortOrderAscIdAsc(fieldId).stream().filter(SubtypeFieldOption::isActive).count()>=100)throw ApiException.validation("A select field may have at most 100 active options.");
        key=upperKey(key);if(options.findByFieldDefinitionIdAndKey(fieldId,key).isPresent())throw ApiException.validation("Option key already exists.");
        SubtypeFieldOption saved=options.saveAndFlush(new SubtypeFieldOption(field,key,required(label,"label",120),order(order)));
        record(p,organization(field.getSubtype()),"FIELD_OPTION",saved.getId(),"CREATED",Map.of("key",key));return saved;}
    @Transactional public SubtypeFieldOption updateOption(AuthPrincipal p,Long id,Long version,String label,int order){requireManage(p);SubtypeFieldOption o=option(id);version(version,o.getVersion());o.update(required(label,"label",120),order(order));options.flush();record(p,organization(o.getFieldDefinition().getSubtype()),"FIELD_OPTION",id,"UPDATED",Map.of("version",o.getVersion()));return o;}
    @Transactional public void setSubtypeActive(AuthPrincipal p,Long id,boolean active){requireManage(p);TicketSubtype s=subtype(id);s.setActive(active);subtypes.flush();record(p,organization(s),"TICKET_SUBTYPE",id,active?"ACTIVATED":"DEACTIVATED",Map.of("active",active));}
    @Transactional public void setFieldActive(AuthPrincipal p,Long id,boolean active){requireManage(p);SubtypeFieldDefinition f=field(id);f.setActive(active);fields.flush();record(p,organization(f.getSubtype()),"SUBTYPE_FIELD",id,active?"ACTIVATED":"DEACTIVATED",Map.of("active",active));}
    @Transactional public void setOptionActive(AuthPrincipal p,Long id,boolean active){requireManage(p);SubtypeFieldOption o=option(id);o.setActive(active);options.flush();record(p,organization(o.getFieldDefinition().getSubtype()),"FIELD_OPTION",id,active?"ACTIVATED":"DEACTIVATED",Map.of("active",active));}
    @Transactional public void reorderSubtypes(AuthPrincipal p,Long typeId,List<Long> ids){requireManage(p);TicketType type=type(typeId);List<TicketSubtype> all=subtypes.findByTicketTypeIdOrderBySortOrderAscIdAsc(typeId);
        if(ids==null||ids.size()!=all.size()||new HashSet<>(ids).size()!=ids.size()||!new HashSet<>(ids).equals(all.stream().map(TicketSubtype::getId).collect(java.util.stream.Collectors.toSet())))throw ApiException.validation("Subtype order must contain every subtype exactly once.");
        Map<Long,TicketSubtype> map=new HashMap<>();all.forEach(s->map.put(s.getId(),s));for(int i=0;i<ids.size();i++)map.get(ids.get(i)).update(map.get(ids.get(i)).getName(),map.get(ids.get(i)).getDescription(),i*10);subtypes.flush();record(p,type.getOrganization(),"TICKET_SUBTYPE",typeId,"REORDERED",Map.of("ids",ids));}
    @Transactional public void reorderFields(AuthPrincipal p,Long subtypeId,List<Long> ids){requireManage(p);TicketSubtype s=subtype(subtypeId);List<SubtypeFieldDefinition> all=fields.findBySubtypeIdOrderBySortOrderAscIdAsc(subtypeId);exact(ids,all.stream().map(SubtypeFieldDefinition::getId).toList(),"Field");Map<Long,SubtypeFieldDefinition> map=new HashMap<>();all.forEach(f->map.put(f.getId(),f));for(int i=0;i<ids.size();i++){SubtypeFieldDefinition f=map.get(ids.get(i));f.update(f.getLabel(),f.getHelpText(),f.isRequired(),f.getVisibility(),i*10);}fields.flush();record(p,organization(s),"SUBTYPE_FIELD",subtypeId,"REORDERED",Map.of("ids",ids));}
    @Transactional public void reorderOptions(AuthPrincipal p,Long fieldId,List<Long> ids){requireManage(p);SubtypeFieldDefinition f=field(fieldId);List<SubtypeFieldOption> all=options.findByFieldDefinitionIdOrderBySortOrderAscIdAsc(fieldId);exact(ids,all.stream().map(SubtypeFieldOption::getId).toList(),"Option");Map<Long,SubtypeFieldOption> map=new HashMap<>();all.forEach(o->map.put(o.getId(),o));for(int i=0;i<ids.size();i++){SubtypeFieldOption o=map.get(ids.get(i));o.update(o.getLabel(),i*10);}options.flush();record(p,organization(f.getSubtype()),"FIELD_OPTION",fieldId,"REORDERED",Map.of("ids",ids));}
    @Transactional public void deleteSubtype(AuthPrincipal p,Long id){requireManage(p);TicketSubtype s=subtype(id);if(count("ticket","subtype_id",id)>0||count("subtype_field_definition","subtype_id",id)>0||count("subtype_routing_rule","subtype_id",id)>0)throw ApiException.conflict("Subtype is referenced and can only be deactivated.");subtypes.delete(s);record(p,organization(s),"TICKET_SUBTYPE",id,"DELETED",Map.of("key",s.getKey()));}
    @Transactional public void deleteField(AuthPrincipal p,Long id){requireManage(p);SubtypeFieldDefinition f=field(id);if(count("ticket_field_value","field_definition_id",id)>0||count("subtype_field_option","field_definition_id",id)>0)throw ApiException.conflict("Field is referenced and can only be deactivated.");fields.delete(f);record(p,organization(f.getSubtype()),"SUBTYPE_FIELD",id,"DELETED",Map.of("key",f.getKey()));}
    @Transactional public void deleteOption(AuthPrincipal p,Long id){requireManage(p);SubtypeFieldOption o=option(id);if(count("ticket_field_value","selected_option_id",id)>0||count("ticket_field_value_option","option_id",id)>0)throw ApiException.conflict("Option is referenced and can only be deactivated.");options.delete(o);record(p,organization(o.getFieldDefinition().getSubtype()),"FIELD_OPTION",id,"DELETED",Map.of("key",o.getKey()));}

    @Transactional(readOnly=true) public SubtypeRoutingRule getRouting(AuthPrincipal p,Long subtypeId,Long organizationId){
        requireManage(p);TicketSubtype subtype=subtype(subtypeId);Organization scope=routingScope(subtype,organizationId);
        return rule(subtypeId,scope);
    }
    @Transactional public SubtypeRoutingRule putRouting(AuthPrincipal p,Long subtypeId,Long organizationId,Long teamId,
            Long primaryId,Long fallbackId,Long approverId,boolean active,Long suppliedVersion){
        requireManage(p);TicketSubtype subtype=subtype(subtypeId);Organization scope=routingScope(subtype,organizationId);
        if(teamId==null)throw ApiException.validation("teamId is required.");
        DeveloperTeam team=teams.findById(teamId)
                .orElseThrow(()->ApiException.notFound("Team not found: "+teamId));
        AppUser primary=selectableMember(primaryId,team,"Primary developer");
        AppUser fallback=selectableMember(fallbackId,team,"Fallback developer");
        AppUser approver=selectableMember(approverId,team,"Approver");
        Optional<SubtypeRoutingRule> existing=findRule(subtypeId,scope);
        SubtypeRoutingRule saved;
        if(existing.isPresent()){
            saved=existing.get();version(suppliedVersion,saved.getVersion());saved.update(scope,team,primary,fallback,approver,active);routing.flush();
        }else{
            if(suppliedVersion!=null)throw ApiException.conflict("Routing configuration does not exist.");
            saved=routing.saveAndFlush(new SubtypeRoutingRule(subtype,scope,team,primary,fallback,approver));saved.setActive(active);routing.flush();
        }
        record(p,organization(subtype),"SUBTYPE_ROUTING",saved.getId(),existing.isPresent()?"UPDATED":"CREATED",Map.of("teamId",teamId,"active",active));return saved;
    }
    @Transactional public void deactivateRouting(AuthPrincipal p,Long subtypeId,Long organizationId){
        requireManage(p);TicketSubtype subtype=subtype(subtypeId);Organization scope=routingScope(subtype,organizationId);SubtypeRoutingRule rule=rule(subtypeId,scope);
        rule.setActive(false);routing.flush();record(p,organization(subtype),"SUBTYPE_ROUTING",rule.getId(),"DEACTIVATED",Map.of("active",false));
    }

    private long count(String table,String column,Long id){return ((Number)entityManager.createNativeQuery("select count(*) from "+table+" where "+column+" = :id").setParameter("id",id).getSingleResult()).longValue();}
    private void requireManage(AuthPrincipal p){if(p.party()!=Responsibility.TICKETFLOW1||!p.hasPermission("TYPE_MANAGE"))throw ApiException.forbidden("TYPE_MANAGE permission is required.");}
    private TicketType type(Long id){return types.findById(id).orElseThrow(()->ApiException.notFound("Ticket type not found: "+id));}
    private TicketSubtype subtype(Long id){return subtypes.findById(id).orElseThrow(()->ApiException.notFound("Subtype not found: "+id));}
    private SubtypeFieldDefinition field(Long id){return fields.findById(id).orElseThrow(()->ApiException.notFound("Field not found: "+id));}
    private SubtypeFieldOption option(Long id){return options.findById(id).orElseThrow(()->ApiException.notFound("Option not found: "+id));}
    private Organization routingScope(TicketSubtype subtype,Long requested){
        Organization owner=organization(subtype);if(owner!=null){if(requested!=null&&!owner.getId().equals(requested))throw ApiException.notFound("Organization not found: "+requested);return owner;}
        if(requested==null)return null;return organizations.findById(requested).filter(Organization::isActive).orElseThrow(()->ApiException.notFound("Organization not found: "+requested));
    }
    private Optional<SubtypeRoutingRule> findRule(Long subtypeId,Organization scope){return scope==null?routing.findBySubtypeIdAndOrganizationIsNull(subtypeId):routing.findBySubtypeIdAndOrganizationId(subtypeId,scope.getId());}
    private SubtypeRoutingRule rule(Long subtypeId,Organization scope){return findRule(subtypeId,scope).orElseThrow(()->ApiException.notFound("Routing rule not found for subtype: "+subtypeId));}
    private AppUser selectableMember(Long id,DeveloperTeam team,String label){if(id==null)return null;AppUser user=users.findById(id).filter(AppUser::isActive).filter(u->u.getParty()==Responsibility.TICKETFLOW1).orElseThrow(()->ApiException.notFound("User not found: "+id));
        if(!team.getLeader().getId().equals(id)&&team.getMembers().stream().noneMatch(m->m.getId().equals(id)))throw ApiException.validation(label+" must be an active member of the selected team.");return user;}
    private Organization organization(TicketSubtype s){return s.getTicketType().getOrganization();}
    private int order(int v){if(v<0)throw ApiException.validation("sortOrder cannot be negative.");return v;}
    private void version(Long supplied,long current){if(supplied==null||supplied!=current)throw ApiException.conflict("Configuration was modified by another user.");}
    private void exact(List<Long> ids,List<Long> actual,String name){if(ids==null||ids.size()!=actual.size()||new HashSet<>(ids).size()!=ids.size()||!new HashSet<>(ids).equals(new HashSet<>(actual)))throw ApiException.validation(name+" order must contain every item exactly once.");}
    private String required(String v,String field,int max){String t=text(v,max);if(t==null)throw ApiException.validation(field+" is required.");return t;}
    private String text(String v,int max){if(v==null||v.isBlank())return null;String t=v.trim();if(t.length()>max)throw ApiException.validation("Text is too long.");return t;}
    private String upperKey(String v){String k=required(v,"key",50).toUpperCase(Locale.ROOT);if(!k.matches("[A-Z][A-Z0-9_]{1,49}"))throw ApiException.validation("Invalid configuration key.");return k;}
    private String lowerKey(String v){String k=required(v,"key",50).toLowerCase(Locale.ROOT);if(!k.matches("[a-z][a-z0-9_]{1,49}"))throw ApiException.validation("Invalid field key.");return k;}
    private void validateBounds(FieldKind kind,Integer minL,Integer maxL,BigDecimal minN,BigDecimal maxN){if(kind==null)throw ApiException.validation("fieldKind is required.");
        boolean text=kind==FieldKind.SHORT_TEXT||kind==FieldKind.LONG_TEXT;boolean number=kind==FieldKind.INTEGER||kind==FieldKind.DECIMAL;
        if(!text&&(minL!=null||maxL!=null)||!number&&(minN!=null||maxN!=null)||minL!=null&&minL<0||maxL!=null&&maxL<1||minL!=null&&maxL!=null&&minL>maxL||minN!=null&&maxN!=null&&minN.compareTo(maxN)>0)throw ApiException.validation("Bounds do not match the field kind.");}
    private void record(AuthPrincipal p,Organization org,String type,Long id,String action,Object value){try{audit.record(org,p.userId(),type,id,action,null,json.writeValueAsString(value));}catch(JsonProcessingException e){throw new IllegalStateException("Cannot serialize audit metadata",e);}}
}
