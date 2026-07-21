package com.ticketflow1.ticketing.ticketconfig;

import com.fasterxml.jackson.core.JsonProcessingException; import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow1.ticketing.auth.AuthPrincipal; import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.configaudit.ConfigurationAuditService; import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.ticket.Responsibility; import com.ticketflow1.ticketing.workflow.TicketType;
import com.ticketflow1.ticketing.workflow.TicketTypeRepository; import jakarta.persistence.EntityManager;
import java.math.BigDecimal; import java.util.*; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketConfigurationService {
    private final TicketSubtypeRepository subtypes; private final SubtypeFieldDefinitionRepository fields;
    private final SubtypeFieldOptionRepository options; private final TicketTypeRepository types;
    private final ConfigurationAuditService audit; private final EntityManager entityManager; private final ObjectMapper json;
    public TicketConfigurationService(TicketSubtypeRepository subtypes,SubtypeFieldDefinitionRepository fields,
            SubtypeFieldOptionRepository options,TicketTypeRepository types,ConfigurationAuditService audit,
            EntityManager entityManager,ObjectMapper json){this.subtypes=subtypes;this.fields=fields;this.options=options;
        this.types=types;this.audit=audit;this.entityManager=entityManager;this.json=json;}

    @Transactional public TicketSubtype createSubtype(AuthPrincipal p,Long typeId,String key,String name,String description,int order){
        requireManage(p);TicketType type=type(typeId);key=upperKey(key);if(subtypes.findByTicketTypeIdAndKey(typeId,key).isPresent())throw ApiException.validation("Subtype key already exists.");
        TicketSubtype saved=subtypes.saveAndFlush(new TicketSubtype(type,key,required(name,"name",120),text(description,1000),order(order)));
        record(p,type.getOrganization(),"TICKET_SUBTYPE",saved.getId(),"CREATED",Map.of("key",key));return saved;}
    @Transactional public SubtypeFieldDefinition createField(AuthPrincipal p,Long subtypeId,String key,String label,String help,FieldKind kind,
            boolean required,FieldVisibility visibility,int order,Integer minLength,Integer maxLength,BigDecimal minNumber,BigDecimal maxNumber){
        requireManage(p);TicketSubtype subtype=subtype(subtypeId);key=lowerKey(key);if(fields.findBySubtypeIdAndKey(subtypeId,key).isPresent())throw ApiException.validation("Field key already exists.");
        validateBounds(kind,minLength,maxLength,minNumber,maxNumber);SubtypeFieldDefinition field=new SubtypeFieldDefinition(subtype,key,required(label,"label",120),text(help,1000),kind,required,visibility,order(order));
        field.setBounds(minLength,maxLength,minNumber,maxNumber);field=fields.saveAndFlush(field);record(p,organization(subtype),"SUBTYPE_FIELD",field.getId(),"CREATED",Map.of("key",key,"kind",kind));return field;}
    @Transactional public SubtypeFieldOption createOption(AuthPrincipal p,Long fieldId,String key,String label,int order){
        requireManage(p);SubtypeFieldDefinition field=field(fieldId);if(field.getFieldKind()!=FieldKind.SINGLE_SELECT&&field.getFieldKind()!=FieldKind.MULTI_SELECT)throw ApiException.validation("Options are allowed only for select fields.");
        key=upperKey(key);if(options.findByFieldDefinitionIdAndKey(fieldId,key).isPresent())throw ApiException.validation("Option key already exists.");
        SubtypeFieldOption saved=options.saveAndFlush(new SubtypeFieldOption(field,key,required(label,"label",120),order(order)));
        record(p,organization(field.getSubtype()),"FIELD_OPTION",saved.getId(),"CREATED",Map.of("key",key));return saved;}
    @Transactional public void setSubtypeActive(AuthPrincipal p,Long id,boolean active){requireManage(p);TicketSubtype s=subtype(id);s.setActive(active);subtypes.flush();record(p,organization(s),"TICKET_SUBTYPE",id,active?"ACTIVATED":"DEACTIVATED",Map.of("active",active));}
    @Transactional public void setFieldActive(AuthPrincipal p,Long id,boolean active){requireManage(p);SubtypeFieldDefinition f=field(id);f.setActive(active);fields.flush();record(p,organization(f.getSubtype()),"SUBTYPE_FIELD",id,active?"ACTIVATED":"DEACTIVATED",Map.of("active",active));}
    @Transactional public void setOptionActive(AuthPrincipal p,Long id,boolean active){requireManage(p);SubtypeFieldOption o=option(id);o.setActive(active);options.flush();record(p,organization(o.getFieldDefinition().getSubtype()),"FIELD_OPTION",id,active?"ACTIVATED":"DEACTIVATED",Map.of("active",active));}
    @Transactional public void reorderSubtypes(AuthPrincipal p,Long typeId,List<Long> ids){requireManage(p);TicketType type=type(typeId);List<TicketSubtype> all=subtypes.findByTicketTypeIdOrderBySortOrderAscIdAsc(typeId);
        if(ids==null||ids.size()!=all.size()||new HashSet<>(ids).size()!=ids.size()||!new HashSet<>(ids).equals(all.stream().map(TicketSubtype::getId).collect(java.util.stream.Collectors.toSet())))throw ApiException.validation("Subtype order must contain every subtype exactly once.");
        Map<Long,TicketSubtype> map=new HashMap<>();all.forEach(s->map.put(s.getId(),s));for(int i=0;i<ids.size();i++)map.get(ids.get(i)).update(map.get(ids.get(i)).getName(),map.get(ids.get(i)).getDescription(),i*10);subtypes.flush();record(p,type.getOrganization(),"TICKET_SUBTYPE",typeId,"REORDERED",Map.of("ids",ids));}
    @Transactional public void deleteSubtype(AuthPrincipal p,Long id){requireManage(p);TicketSubtype s=subtype(id);if(count("ticket","subtype_id",id)>0||count("subtype_field_definition","subtype_id",id)>0||count("subtype_routing_rule","subtype_id",id)>0)throw ApiException.conflict("Subtype is referenced and can only be deactivated.");subtypes.delete(s);record(p,organization(s),"TICKET_SUBTYPE",id,"DELETED",Map.of("key",s.getKey()));}
    @Transactional public void deleteField(AuthPrincipal p,Long id){requireManage(p);SubtypeFieldDefinition f=field(id);if(count("ticket_field_value","field_definition_id",id)>0||count("subtype_field_option","field_definition_id",id)>0)throw ApiException.conflict("Field is referenced and can only be deactivated.");fields.delete(f);record(p,organization(f.getSubtype()),"SUBTYPE_FIELD",id,"DELETED",Map.of("key",f.getKey()));}
    @Transactional public void deleteOption(AuthPrincipal p,Long id){requireManage(p);SubtypeFieldOption o=option(id);if(count("ticket_field_value","selected_option_id",id)>0||count("ticket_field_value_option","option_id",id)>0)throw ApiException.conflict("Option is referenced and can only be deactivated.");options.delete(o);record(p,organization(o.getFieldDefinition().getSubtype()),"FIELD_OPTION",id,"DELETED",Map.of("key",o.getKey()));}

    private long count(String table,String column,Long id){return ((Number)entityManager.createNativeQuery("select count(*) from "+table+" where "+column+" = :id").setParameter("id",id).getSingleResult()).longValue();}
    private void requireManage(AuthPrincipal p){if(p.party()!=Responsibility.TICKETFLOW1||!p.hasPermission("TYPE_MANAGE"))throw ApiException.forbidden("TYPE_MANAGE permission is required.");}
    private TicketType type(Long id){return types.findById(id).orElseThrow(()->ApiException.notFound("Ticket type not found: "+id));}
    private TicketSubtype subtype(Long id){return subtypes.findById(id).orElseThrow(()->ApiException.notFound("Subtype not found: "+id));}
    private SubtypeFieldDefinition field(Long id){return fields.findById(id).orElseThrow(()->ApiException.notFound("Field not found: "+id));}
    private SubtypeFieldOption option(Long id){return options.findById(id).orElseThrow(()->ApiException.notFound("Option not found: "+id));}
    private Organization organization(TicketSubtype s){return s.getTicketType().getOrganization();}
    private int order(int v){if(v<0)throw ApiException.validation("sortOrder cannot be negative.");return v;}
    private String required(String v,String field,int max){String t=text(v,max);if(t==null)throw ApiException.validation(field+" is required.");return t;}
    private String text(String v,int max){if(v==null||v.isBlank())return null;String t=v.trim();if(t.length()>max)throw ApiException.validation("Text is too long.");return t;}
    private String upperKey(String v){String k=required(v,"key",50).toUpperCase(Locale.ROOT);if(!k.matches("[A-Z][A-Z0-9_]{1,49}"))throw ApiException.validation("Invalid configuration key.");return k;}
    private String lowerKey(String v){String k=required(v,"key",50).toLowerCase(Locale.ROOT);if(!k.matches("[a-z][a-z0-9_]{1,49}"))throw ApiException.validation("Invalid field key.");return k;}
    private void validateBounds(FieldKind kind,Integer minL,Integer maxL,BigDecimal minN,BigDecimal maxN){if(kind==null)throw ApiException.validation("fieldKind is required.");
        boolean text=kind==FieldKind.SHORT_TEXT||kind==FieldKind.LONG_TEXT;boolean number=kind==FieldKind.INTEGER||kind==FieldKind.DECIMAL;
        if(!text&&(minL!=null||maxL!=null)||!number&&(minN!=null||maxN!=null)||minL!=null&&minL<0||maxL!=null&&maxL<1||minL!=null&&maxL!=null&&minL>maxL||minN!=null&&maxN!=null&&minN.compareTo(maxN)>0)throw ApiException.validation("Bounds do not match the field kind.");}
    private void record(AuthPrincipal p,Organization org,String type,Long id,String action,Object value){try{audit.record(org,p.userId(),type,id,action,null,json.writeValueAsString(value));}catch(JsonProcessingException e){throw new IllegalStateException("Cannot serialize audit metadata",e);}}
}
