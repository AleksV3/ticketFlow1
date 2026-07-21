package com.ticketflow1.ticketing.team;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.ticket.*;
import com.ticketflow1.ticketing.user.*;
import java.util.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ticketflow1.ticketing.workflow.TicketTransitionService;

@Service
public class DeveloperTeamService {
    private final DeveloperTeamRepository teams; private final AppUserRepository users; private final TicketRepository tickets; private final TicketTransitionService transitions; private final EntityManager entityManager;
    public DeveloperTeamService(DeveloperTeamRepository teams,AppUserRepository users,TicketRepository tickets,TicketTransitionService transitions,EntityManager entityManager){this.teams=teams;this.users=users;this.tickets=tickets;this.transitions=transitions;this.entityManager=entityManager;}

    @Transactional(readOnly=true) public List<TeamDtos.TeamResponse> list(AuthPrincipal p){
        List<DeveloperTeam> visible=p.party()==Responsibility.TICKETFLOW1?teams.findAllByOrderByNameAsc():teams.findDistinctByLeaderIdOrMembersIdOrderByNameAsc(p.userId(),p.userId());
        return visible.stream().map(t->response(t,p)).toList();
    }
    @Transactional(readOnly=true) public TeamDtos.Options options(AuthPrincipal p){
        var people=availablePeople(p).stream().map(TeamDtos.TeamResponse::person).toList();
        var visibleTickets=p.party()==Responsibility.CLIENT?tickets.findByOrganizationId(p.organizationId()):tickets.findAll();
        var refs=visibleTickets.stream().map(t->TeamDtos.TeamResponse.ticket(t,ticket->transitions.allowedTransitions(ticket,p))).sorted(Comparator.comparing(TeamDtos.TicketRef::ticketKey)).toList();
        return new TeamDtos.Options(people,refs);
    }
    @Transactional public TeamDtos.TeamResponse create(TeamDtos.SaveTeamRequest r,AuthPrincipal p){
        internal(p);AppUser actor=user(p.userId());AppUser leader=p.hasPermission("USER_MANAGE")&&r.leaderId()!=null?selectableUser(r.leaderId(),p):actor;
        DeveloperTeam team=new DeveloperTeam(name(r.name()),text(r.description()),leader,actor);apply(team,r,leader,p);return response(teams.save(team),p);
    }
    @Transactional public TeamDtos.TeamResponse update(Long id,TeamDtos.SaveTeamRequest r,AuthPrincipal p){
        DeveloperTeam team=teams.findById(id).orElseThrow(()->ApiException.notFound("Team not found."));
        if(!canView(team,p))throw ApiException.notFound("Team not found.");
        if(!canEdit(team,p))throw ApiException.forbidden("Only an administrator or this team's leader can edit it.");
        AppUser leader=r.leaderId()==null?team.getLeader():selectableUser(r.leaderId(),p);apply(team,r,leader,p);return response(teams.save(team),p);
    }
    @Transactional public TeamDtos.TeamResponse reorder(Long id,TeamDtos.ReorderTicketsRequest r,AuthPrincipal p){
        DeveloperTeam team=teams.findById(id).orElseThrow(()->ApiException.notFound("Team not found."));
        if(!canView(team,p))throw ApiException.notFound("Team not found.");
        List<Ticket> visible=team.getTickets().stream().filter(ticket->ticketVisible(ticket,p)).toList();
        List<String> keys=r.ticketKeys()==null?List.of():r.ticketKeys();
        if(keys.size()!=visible.size()||new HashSet<>(keys).size()!=keys.size()||!new HashSet<>(keys).equals(visible.stream().map(Ticket::getTicketKey).collect(java.util.stream.Collectors.toSet())))throw ApiException.validation("ticketKeys must contain every visible team ticket exactly once.");
        Map<String,Ticket> byKey=visible.stream().collect(java.util.stream.Collectors.toMap(Ticket::getTicketKey,java.util.function.Function.identity()));
        Iterator<Ticket> reorderedVisible=keys.stream().map(byKey::get).iterator();List<Ticket> merged=new ArrayList<>();
        for(Ticket ticket:team.getTickets())merged.add(ticketVisible(ticket,p)?reorderedVisible.next():ticket);
        teams.parkTicketPositions(team.getId());
        for(int position=0;position<merged.size();position++)teams.updateTicketPosition(team.getId(),merged.get(position).getId(),position);
        entityManager.clear();DeveloperTeam reordered=teams.findById(id).orElseThrow(()->ApiException.notFound("Team not found."));return response(reordered,p);
    }
    private void apply(DeveloperTeam team,TeamDtos.SaveTeamRequest r,AppUser leader,AuthPrincipal p){
        Set<Long> ids=r.memberIds()==null?Set.of():r.memberIds();Set<AppUser> members=new LinkedHashSet<>(users.findAllById(ids));
        if(members.size()!=ids.size()||members.stream().anyMatch(u->!u.isActive()||!canSelect(u,p)))throw ApiException.validation("All members must be active users available to you.");
        List<String> keys=r.ticketKeys()==null?List.of():r.ticketKeys();if(new HashSet<>(keys).size()!=keys.size())throw ApiException.validation("A ticket can only be added to a team once.");List<Ticket> related=new ArrayList<>();
        for(String key:keys){Ticket ticket=tickets.findByTicketKey(key).orElseThrow(()->ApiException.validation("Ticket not found: "+key));if(!ticketVisible(ticket,p))throw ApiException.validation("Ticket not found: "+key);related.add(ticket);}
        team.update(name(r.name()),text(r.description()),leader,members,related);
    }
    private TeamDtos.TeamResponse response(DeveloperTeam t,AuthPrincipal p){return TeamDtos.TeamResponse.from(t,canEdit(t,p),ticket->ticketVisible(ticket,p),ticket->transitions.allowedTransitions(ticket,p));}
    private boolean canView(DeveloperTeam t,AuthPrincipal p){return p.party()==Responsibility.TICKETFLOW1||t.getLeader().getId().equals(p.userId())||t.getMembers().stream().anyMatch(u->u.getId().equals(p.userId()));}
    private boolean canEdit(DeveloperTeam t,AuthPrincipal p){return p.party()==Responsibility.TICKETFLOW1&&(p.hasPermission("USER_MANAGE")||t.getLeader().getId().equals(p.userId()));}
    private boolean ticketVisible(Ticket t,AuthPrincipal p){return p.party()==Responsibility.TICKETFLOW1||t.getOrganization().getId().equals(p.organizationId());}
    private List<AppUser> availablePeople(AuthPrincipal p){return p.party()==Responsibility.TICKETFLOW1?users.findByActiveTrueOrderByDisplayNameAsc():users.findByActiveTrueAndOrganizationIdOrderByDisplayNameAsc(p.organizationId());}
    private boolean canSelect(AppUser u,AuthPrincipal p){return p.party()==Responsibility.TICKETFLOW1||(u.getOrganization()!=null&&u.getOrganization().getId().equals(p.organizationId()));}
    private AppUser selectableUser(Long id,AuthPrincipal p){AppUser u=user(id);if(!u.isActive()||!canSelect(u,p))throw ApiException.validation("Team member is not available to you.");return u;}
    private AppUser user(Long id){return users.findById(id).orElseThrow(()->ApiException.notFound("User not found."));}
    private void internal(AuthPrincipal p){if(p.party()!=Responsibility.TICKETFLOW1)throw ApiException.forbidden("Only TicketFlow1 users can create teams.");} private String name(String v){if(v==null||v.isBlank())throw ApiException.validation("Team name is required.");return v.trim();} private String text(String v){return v==null||v.isBlank()?null:v.trim();}
}
