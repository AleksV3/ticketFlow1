package com.ticketflow1.ticketing.ticketconfig;
import com.ticketflow1.ticketing.common.Auditable; import com.ticketflow1.ticketing.organization.Organization;
import com.ticketflow1.ticketing.team.DeveloperTeam; import com.ticketflow1.ticketing.user.AppUser;
import jakarta.persistence.*; import java.time.Instant; import org.hibernate.annotations.CreationTimestamp;
@Entity @Table(name="subtype_routing_rule")
public class SubtypeRoutingRule extends Auditable {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="subtype_id") private TicketSubtype subtype;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="organization_id") private Organization organization;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="team_id") private DeveloperTeam team;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="primary_developer_id") private AppUser primaryDeveloper;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="fallback_developer_id") private AppUser fallbackDeveloper;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="approver_id") private AppUser approver;
    @Column(nullable=false) private boolean active=true; @Version @Column(nullable=false) private long version;
    @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected SubtypeRoutingRule() {}
    public SubtypeRoutingRule(TicketSubtype subtype,Organization organization,DeveloperTeam team,AppUser primaryDeveloper,AppUser fallbackDeveloper,AppUser approver){
        this.subtype=subtype;this.organization=organization;this.team=team;this.primaryDeveloper=primaryDeveloper;this.fallbackDeveloper=fallbackDeveloper;this.approver=approver;}
    public Long getId(){return id;} public TicketSubtype getSubtype(){return subtype;} public Organization getOrganization(){return organization;}
    public DeveloperTeam getTeam(){return team;} public AppUser getPrimaryDeveloper(){return primaryDeveloper;} public AppUser getFallbackDeveloper(){return fallbackDeveloper;}
    public AppUser getApprover(){return approver;} public boolean isActive(){return active;} public long getVersion(){return version;} public void setActive(boolean active){this.active=active;}
    public void update(Organization organization,DeveloperTeam team,AppUser primaryDeveloper,AppUser fallbackDeveloper,AppUser approver,boolean active){
        this.organization=organization;this.team=team;this.primaryDeveloper=primaryDeveloper;this.fallbackDeveloper=fallbackDeveloper;this.approver=approver;this.active=active;
    }
}
