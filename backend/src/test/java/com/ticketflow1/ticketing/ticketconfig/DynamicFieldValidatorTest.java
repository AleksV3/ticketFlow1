package com.ticketflow1.ticketing.ticketconfig;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.configaudit.ConfigurationAuditService;
import com.ticketflow1.ticketing.ticket.Responsibility;
import com.ticketflow1.ticketing.workflow.TicketTypeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DynamicFieldValidatorTest {
    @Mock TicketSubtypeRepository subtypes; @Mock SubtypeFieldDefinitionRepository fields;
    @Mock SubtypeFieldOptionRepository options; @Mock TicketTypeRepository types;
    @Mock ConfigurationAuditService audit; @Mock EntityManager entityManager; @Mock Query query;
    private final DynamicFieldValidator validator=new DynamicFieldValidator();

    @Test void validatesTypedBoundsAndRequiredValues(){
        SubtypeFieldDefinition integer=field("count",FieldKind.INTEGER,true);integer.setBounds(null,null,BigDecimal.ONE,BigDecimal.TEN);
        validator.validate(integer,5,List.of());
        assertThatThrownBy(()->validator.validate(integer,1.5,List.of())).isInstanceOf(ApiException.class).hasMessageContaining("whole number");
        assertThatThrownBy(()->validator.validate(integer,11,List.of())).isInstanceOf(ApiException.class).hasMessageContaining("maximum");
        assertThatThrownBy(()->validator.validate(integer,null,List.of())).isInstanceOf(ApiException.class).hasMessageContaining("required");
    }
    @Test void acceptsOnlyActiveConfiguredOptions(){
        SubtypeFieldDefinition select=field("direction",FieldKind.SINGLE_SELECT,true);
        SubtypeFieldOption active=new SubtypeFieldOption(select,"INBOUND","Inbound",0);
        SubtypeFieldOption inactive=new SubtypeFieldOption(select,"OUTBOUND","Outbound",10);inactive.setActive(false);
        validator.validate(select,"INBOUND",List.of(active,inactive));
        assertThatThrownBy(()->validator.validate(select,"OUTBOUND",List.of(active,inactive))).isInstanceOf(ApiException.class).hasMessageContaining("unavailable option");
    }
    @Test void safeDeleteRejectsReferencedSubtype(){
        TicketSubtype subtype=orgSubtype(11L);when(subtypes.findById(11L)).thenReturn(java.util.Optional.of(subtype));
        when(entityManager.createNativeQuery(any())).thenReturn(query);when(query.setParameter("id",11L)).thenReturn(query);when(query.getSingleResult()).thenReturn(1L);
        assertThatThrownBy(()->service().deleteSubtype(principal(),11L)).isInstanceOf(ApiException.class).hasMessageContaining("deactivated");
    }
    @Test void activeStateAndOrderingAreAudited(){
        TicketSubtype first=orgSubtype(1L),second=orgSubtype(2L);var type=first.getTicketType();
        when(subtypes.findById(1L)).thenReturn(java.util.Optional.of(first));service().setSubtypeActive(principal(),1L,false);
        org.assertj.core.api.Assertions.assertThat(first.isActive()).isFalse();verify(audit).record(any(),any(),any(),any(),any(),any(),any());
        when(types.findById(type.getId())).thenReturn(java.util.Optional.of(type));when(subtypes.findByTicketTypeIdOrderBySortOrderAscIdAsc(type.getId())).thenReturn(List.of(first,second));
        service().reorderSubtypes(principal(),type.getId(),List.of(2L,1L));
        org.assertj.core.api.Assertions.assertThat(second.getSortOrder()).isZero();org.assertj.core.api.Assertions.assertThat(first.getSortOrder()).isEqualTo(10);
    }
    private TicketConfigurationService service(){return new TicketConfigurationService(subtypes,fields,options,types,audit,entityManager,new ObjectMapper());}
    private AuthPrincipal principal(){return new AuthPrincipal(99L,Responsibility.TICKETFLOW1,null,Set.of("TYPE_MANAGE"));}
    private SubtypeFieldDefinition field(String key,FieldKind kind,boolean required){return new SubtypeFieldDefinition(orgSubtype(1L),key,key,null,kind,required,FieldVisibility.INTERNAL,0);}
    private TicketSubtype orgSubtype(Long id){var org=new com.ticketflow1.ticketing.organization.Organization("Internal");ReflectionTestUtils.setField(org,"id",7L);
        var type=new com.ticketflow1.ticketing.workflow.TicketType("TASI","TASI",null,org,false,false);ReflectionTestUtils.setField(type,"id",9L);
        var subtype=new TicketSubtype(type,"FIREWALL","Firewall",null,0);ReflectionTestUtils.setField(subtype,"id",id);return subtype;}
}
