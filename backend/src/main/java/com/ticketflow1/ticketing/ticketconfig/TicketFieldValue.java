package com.ticketflow1.ticketing.ticketconfig;

import com.ticketflow1.ticketing.common.Auditable; import com.ticketflow1.ticketing.team.DeveloperTeam;
import com.ticketflow1.ticketing.ticket.Ticket; import com.ticketflow1.ticketing.user.AppUser;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.Instant; import java.time.LocalDate;
import java.util.LinkedHashSet; import java.util.Set; import org.hibernate.annotations.CreationTimestamp;

@Entity @Table(name="ticket_field_value")
public class TicketFieldValue extends Auditable {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="ticket_id") private Ticket ticket;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="field_definition_id") private SubtypeFieldDefinition fieldDefinition;
    @Column(name="text_value",columnDefinition="TEXT") private String textValue;
    @Column(name="number_value",precision=19,scale=4) private BigDecimal numberValue;
    @Column(name="date_value") private LocalDate dateValue;
    @Column(name="boolean_value") private Boolean booleanValue;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="selected_option_id") private SubtypeFieldOption selectedOption;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_value_id") private AppUser userValue;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="team_value_id") private DeveloperTeam teamValue;
    @Column(name="reference_snapshot",length=255) private String referenceSnapshot;
    @ManyToMany @JoinTable(name="ticket_field_value_option",joinColumns=@JoinColumn(name="field_value_id"),
        inverseJoinColumns=@JoinColumn(name="option_id")) private Set<SubtypeFieldOption> selectedOptions=new LinkedHashSet<>();
    @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected TicketFieldValue() {}
    public TicketFieldValue(Ticket ticket,SubtypeFieldDefinition fieldDefinition){this.ticket=ticket;this.fieldDefinition=fieldDefinition;}
    public Long getId(){return id;} public Ticket getTicket(){return ticket;} public SubtypeFieldDefinition getFieldDefinition(){return fieldDefinition;}
    public String getTextValue(){return textValue;} public BigDecimal getNumberValue(){return numberValue;} public LocalDate getDateValue(){return dateValue;}
    public Boolean getBooleanValue(){return booleanValue;} public SubtypeFieldOption getSelectedOption(){return selectedOption;}
    public AppUser getUserValue(){return userValue;} public DeveloperTeam getTeamValue(){return teamValue;} public String getReferenceSnapshot(){return referenceSnapshot;}
    public Set<SubtypeFieldOption> getSelectedOptions(){return selectedOptions;}
    public void clear(){textValue=null;numberValue=null;dateValue=null;booleanValue=null;selectedOption=null;userValue=null;teamValue=null;referenceSnapshot=null;selectedOptions.clear();}
    public void setText(String v){clear();textValue=v;} public void setNumber(BigDecimal v){clear();numberValue=v;} public void setDate(LocalDate v){clear();dateValue=v;}
    public void setBoolean(Boolean v){clear();booleanValue=v;} public void setOption(SubtypeFieldOption v){clear();selectedOption=v;}
    public void setUser(AppUser v,String snapshot){clear();userValue=v;referenceSnapshot=snapshot;} public void setTeam(DeveloperTeam v,String snapshot){clear();teamValue=v;referenceSnapshot=snapshot;}
    public void setOptions(Set<SubtypeFieldOption> v){clear();selectedOptions.addAll(v);}
}
