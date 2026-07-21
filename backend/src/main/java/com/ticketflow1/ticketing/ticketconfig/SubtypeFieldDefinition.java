package com.ticketflow1.ticketing.ticketconfig;

import com.ticketflow1.ticketing.common.Auditable;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "subtype_field_definition")
public class SubtypeFieldDefinition extends Auditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "subtype_id") private TicketSubtype subtype;
    @Column(nullable = false, length = 50) private String key;
    @Column(nullable = false, length = 120) private String label;
    @Column(name = "help_text", length = 1000) private String helpText;
    @Enumerated(EnumType.STRING) @Column(name = "field_kind", nullable = false, length = 20) private FieldKind fieldKind;
    @Column(nullable = false) private boolean required;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private FieldVisibility visibility;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "min_length") private Integer minLength;
    @Column(name = "max_length") private Integer maxLength;
    @Column(name = "min_number", precision = 19, scale = 4) private BigDecimal minNumber;
    @Column(name = "max_number", precision = 19, scale = 4) private BigDecimal maxNumber;
    @Version @Column(nullable = false) private long version;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected SubtypeFieldDefinition() {}
    public SubtypeFieldDefinition(TicketSubtype subtype,String key,String label,String helpText,FieldKind fieldKind,
            boolean required,FieldVisibility visibility,int sortOrder){this.subtype=subtype;this.key=key;this.label=label;
        this.helpText=helpText;this.fieldKind=fieldKind;this.required=required;this.visibility=visibility;this.sortOrder=sortOrder;}
    public Long getId(){return id;} public TicketSubtype getSubtype(){return subtype;} public String getKey(){return key;}
    public String getLabel(){return label;} public String getHelpText(){return helpText;} public FieldKind getFieldKind(){return fieldKind;}
    public boolean isRequired(){return required;} public FieldVisibility getVisibility(){return visibility;} public boolean isActive(){return active;}
    public int getSortOrder(){return sortOrder;} public Integer getMinLength(){return minLength;} public Integer getMaxLength(){return maxLength;}
    public BigDecimal getMinNumber(){return minNumber;} public BigDecimal getMaxNumber(){return maxNumber;} public long getVersion(){return version;}
    public void setBounds(Integer minLength,Integer maxLength,BigDecimal minNumber,BigDecimal maxNumber){this.minLength=minLength;
        this.maxLength=maxLength;this.minNumber=minNumber;this.maxNumber=maxNumber;}
    public void update(String label,String helpText,boolean required,FieldVisibility visibility,int sortOrder){this.label=label;
        this.helpText=helpText;this.required=required;this.visibility=visibility;this.sortOrder=sortOrder;}
    public void setActive(boolean active){this.active=active;}
}
