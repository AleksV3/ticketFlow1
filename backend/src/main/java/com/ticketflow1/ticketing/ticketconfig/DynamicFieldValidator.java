package com.ticketflow1.ticketing.ticketconfig;

import com.ticketflow1.ticketing.common.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DynamicFieldValidator {
    public void validate(SubtypeFieldDefinition field,Object value,Collection<SubtypeFieldOption> options){
        if(value==null||(value instanceof String s&&s.isBlank())||(value instanceof Collection<?> c&&c.isEmpty())){
            if(field.isRequired())fail(field,"is required"); return;
        }
        switch(field.getFieldKind()){
            case SHORT_TEXT,LONG_TEXT -> text(field,value);
            case INTEGER -> number(field,value,true);
            case DECIMAL -> number(field,value,false);
            case DATE -> date(field,value);
            case BOOLEAN -> {if(!(value instanceof Boolean))fail(field,"must be true or false");}
            case SINGLE_SELECT -> option(field,value,options,false);
            case MULTI_SELECT -> option(field,value,options,true);
            case USER_REFERENCE,TEAM_REFERENCE -> {if(!(value instanceof Number n)||n.longValue()<=0)fail(field,"must be a valid reference ID");}
        }
    }
    private void text(SubtypeFieldDefinition f,Object v){if(!(v instanceof String s))fail(f,"must be text");
        String s=(String)v; if(f.getMinLength()!=null&&s.length()<f.getMinLength())fail(f,"is too short");
        if(f.getMaxLength()!=null&&s.length()>f.getMaxLength())fail(f,"is too long");}
    private void number(SubtypeFieldDefinition f,Object v,boolean integer){BigDecimal n;
        try{n=v instanceof Number?new BigDecimal(v.toString()):new BigDecimal((String)v);}catch(Exception e){fail(f,"must be a number");return;}
        if(integer&&n.stripTrailingZeros().scale()>0)fail(f,"must be a whole number");
        if(f.getMinNumber()!=null&&n.compareTo(f.getMinNumber())<0)fail(f,"is below the minimum");
        if(f.getMaxNumber()!=null&&n.compareTo(f.getMaxNumber())>0)fail(f,"is above the maximum");}
    private void date(SubtypeFieldDefinition f,Object v){if(v instanceof LocalDate)return;if(!(v instanceof String))fail(f,"must be an ISO date");
        try{LocalDate.parse((String)v);}catch(DateTimeParseException e){fail(f,"must be an ISO date");}}
    private void option(SubtypeFieldDefinition f,Object v,Collection<SubtypeFieldOption> options,boolean multi){
        Set<String> allowed=options.stream().filter(SubtypeFieldOption::isActive).map(SubtypeFieldOption::getKey).collect(Collectors.toSet());
        if(multi){if(!(v instanceof Collection<?> c)||c.stream().anyMatch(x->!(x instanceof String)||!allowed.contains(x)))fail(f,"contains an unavailable option");}
        else if(!(v instanceof String s)||!allowed.contains(s))fail(f,"contains an unavailable option");}
    private void fail(SubtypeFieldDefinition f,String message){throw ApiException.validation(f.getKey()+" "+message+".");}
}
