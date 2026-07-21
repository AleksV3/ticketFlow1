package com.ticketflow1.ticketing.ticketconfig;

import java.math.BigDecimal;
import java.util.List;

public final class TicketConfigurationDtos {
    private TicketConfigurationDtos() {}
    public record CreateSubtype(String key,String name,String description,Integer sortOrder) {}
    public record UpdateSubtype(Long version,String name,String description,Integer sortOrder) {}
    public record CreateField(String key,String label,String helpText,FieldKind fieldKind,boolean required,
            FieldVisibility visibility,Integer sortOrder,Integer minLength,Integer maxLength,BigDecimal minNumber,BigDecimal maxNumber) {}
    public record UpdateField(Long version,String label,String helpText,boolean required,FieldVisibility visibility,
            Integer sortOrder,Integer minLength,Integer maxLength,BigDecimal minNumber,BigDecimal maxNumber) {}
    public record CreateOption(String key,String label,Integer sortOrder) {}
    public record UpdateOption(Long version,String label,Integer sortOrder) {}
    public record PutRouting(Long organizationId,Long teamId,Long primaryDeveloperId,Long fallbackDeveloperId,
            Long approverId,boolean active,Long version) {}
    public record Reorder(List<Long> ids) {}
    public record SubtypeResponse(Long id,Long ticketTypeId,String key,String name,String description,boolean active,int sortOrder,long version){
        public static SubtypeResponse from(TicketSubtype s){return new SubtypeResponse(s.getId(),s.getTicketType().getId(),s.getKey(),s.getName(),s.getDescription(),s.isActive(),s.getSortOrder(),s.getVersion());}}
    public record FieldResponse(Long id,Long subtypeId,String key,String label,String helpText,FieldKind fieldKind,
            boolean required,FieldVisibility visibility,boolean active,int sortOrder,Integer minLength,Integer maxLength,
            BigDecimal minNumber,BigDecimal maxNumber,long version){
        public static FieldResponse from(SubtypeFieldDefinition f){return new FieldResponse(f.getId(),f.getSubtype().getId(),f.getKey(),f.getLabel(),f.getHelpText(),f.getFieldKind(),f.isRequired(),f.getVisibility(),f.isActive(),f.getSortOrder(),f.getMinLength(),f.getMaxLength(),f.getMinNumber(),f.getMaxNumber(),f.getVersion());}}
    public record OptionResponse(Long id,Long fieldId,String key,String label,boolean active,int sortOrder,long version){
        public static OptionResponse from(SubtypeFieldOption o){return new OptionResponse(o.getId(),o.getFieldDefinition().getId(),o.getKey(),o.getLabel(),o.isActive(),o.getSortOrder(),o.getVersion());}}
    public record RoutingResponse(Long id,Long subtypeId,Long organizationId,Long teamId,Long primaryDeveloperId,
            Long fallbackDeveloperId,Long approverId,boolean active,long version){
        public static RoutingResponse from(SubtypeRoutingRule r){return new RoutingResponse(r.getId(),r.getSubtype().getId(),
                r.getOrganization()==null?null:r.getOrganization().getId(),r.getTeam().getId(),
                r.getPrimaryDeveloper()==null?null:r.getPrimaryDeveloper().getId(),
                r.getFallbackDeveloper()==null?null:r.getFallbackDeveloper().getId(),
                r.getApprover()==null?null:r.getApprover().getId(),r.isActive(),r.getVersion());}}
}
