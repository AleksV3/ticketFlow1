package com.ticketflow1.ticketing.ticketconfig;

import com.ticketflow1.ticketing.rbac.Role;
import jakarta.persistence.*;

@Entity
@Table(name="subtype_field_role_grant", uniqueConstraints=@UniqueConstraint(name="uq_subtype_field_role_operation", columnNames={"field_id","role_id","operation"}))
public class SubtypeFieldRoleGrant {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="field_id") private SubtypeFieldDefinition field;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="role_id") private Role role;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private FieldGrantOperation operation;
    protected SubtypeFieldRoleGrant() {}
    public SubtypeFieldRoleGrant(SubtypeFieldDefinition field, Role role, FieldGrantOperation operation){this.field=field;this.role=role;this.operation=operation;}
    public Long getId(){return id;} public SubtypeFieldDefinition getField(){return field;} public Role getRole(){return role;} public FieldGrantOperation getOperation(){return operation;}
}
