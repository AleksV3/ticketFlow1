package com.ticketflow1.ticketing.team;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.ticket.*;
import com.ticketflow1.ticketing.user.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ticketflow1.ticketing.workflow.TicketTransitionService;

@Service
public class DeveloperTeamService {
    private final DeveloperTeamRepository teams; private final AppUserRepository users; private final TicketRepository tickets; private final TicketTransitionService transitions;
    public DeveloperTeamService(DeveloperTeamRepository teams,AppUserRepository users,TicketRepository tickets,TicketTransitionService transitions){this.teams=teams;this.users=users;this.tickets=tickets;this.transitions=transitions;}

    @Transactional(readOnly=true) public List<TeamDtos.TeamResponse> list(AuthPrincipal p){internal(p);return teams.findAllByOrderByNameAsc().stream().map(t->TeamDtos.TeamResponse.from(t,canEdit(t,p),ticket->transitions.allowedTransitions(ticket,p))).toList();}
    @Transactional(readOnly=true) public TeamDtos.Options options(AuthPrincipal p){internal(p);var people=users.findByActiveTrueAndPartyOrderByDisplayNameAsc(Responsibility.TICKETFLOW1).stream().map(u->new TeamDtos.Person(u.getId(),u.getDisplayName())).toList();var refs=tickets.findAll().stream().map(t->new TeamDtos.TicketRef(t.getTicketKey(),t.getTitle(),t.getCurrentState().getKey(),transitions.allowedTransitions(t,p))).sorted(Comparator.comparing(TeamDtos.TicketRef::ticketKey)).toList();return new TeamDtos.Options(people,refs);}
    @Transactional public TeamDtos.TeamResponse create(TeamDtos.SaveTeamRequest r,AuthPrincipal p){internal(p);AppUser actor=user(p.userId());AppUser leader=p.hasPermission("USER_MANAGE")&&r.leaderId()!=null?internalUser(r.leaderId()):actor;DeveloperTeam team=new DeveloperTeam(name(r.name()),text(r.description()),leader,actor);apply(team,r,leader);return TeamDtos.TeamResponse.from(teams.save(team),true,ticket->transitions.allowedTransitions(ticket,p));}
    @Transactional public TeamDtos.TeamResponse update(Long id,TeamDtos.SaveTeamRequest r,AuthPrincipal p){internal(p);DeveloperTeam team=teams.findById(id).orElseThrow(()->ApiException.notFound("Team not found."));if(!canEdit(team,p))throw ApiException.forbidden("Only an administrator or this team's leader can edit it.");AppUser leader=r.leaderId()==null?team.getLeader():internalUser(r.leaderId());apply(team,r,leader);return TeamDtos.TeamResponse.from(teams.save(team),true,ticket->transitions.allowedTransitions(ticket,p));}
    private void apply(DeveloperTeam team,TeamDtos.SaveTeamRequest r,AppUser leader){Set<Long> ids=r.developerIds()==null?Set.of():r.developerIds();Set<AppUser> developers=new LinkedHashSet<>(users.findAllById(ids));if(developers.size()!=ids.size()||developers.stream().anyMatch(u->u.getParty()!=Responsibility.TICKETFLOW1))throw ApiException.validation("All developers must be active TicketFlow1 users.");Set<String> keys=r.ticketKeys()==null?Set.of():r.ticketKeys();Set<Ticket> related=new LinkedHashSet<>();for(String key:keys)related.add(tickets.findByTicketKey(key).orElseThrow(()->ApiException.validation("Ticket not found: "+key)));team.update(name(r.name()),text(r.description()),leader,developers,related);}
    private boolean canEdit(DeveloperTeam t,AuthPrincipal p){return p.hasPermission("USER_MANAGE")||t.getLeader().getId().equals(p.userId());}
    private AppUser user(Long id){return users.findById(id).orElseThrow(()->ApiException.notFound("User not found."));} private AppUser internalUser(Long id){AppUser u=user(id);if(u.getParty()!=Responsibility.TICKETFLOW1)throw ApiException.validation("Team members must be TicketFlow1 users.");return u;}
    private void internal(AuthPrincipal p){if(p.party()!=Responsibility.TICKETFLOW1)throw ApiException.forbidden("Developer teams are internal.");} private String name(String v){if(v==null||v.isBlank())throw ApiException.validation("Team name is required.");return v.trim();} private String text(String v){return v==null||v.isBlank()?null:v.trim();}
}
