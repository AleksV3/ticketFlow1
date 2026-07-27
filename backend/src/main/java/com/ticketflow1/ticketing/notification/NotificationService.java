package com.ticketflow1.ticketing.notification;

import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.ticket.TicketRepository;
import com.ticketflow1.ticketing.ticket.Responsibility;
import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.team.DeveloperTeam;
import com.ticketflow1.ticketing.user.AppUser;
import com.ticketflow1.ticketing.user.AppUserRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final AppUserRepository users;
    private final TicketRepository tickets;
    private final TicketFollowerRepository followers;
    public NotificationService(NotificationRepository notifications, AppUserRepository users, TicketRepository tickets, TicketFollowerRepository followers){this.notifications=notifications;this.users=users;this.tickets=tickets;this.followers=followers;}

    @Transactional(readOnly=true) public boolean isFollowing(String key, AuthPrincipal principal){return followers.existsByIdTicketIdAndIdUserId(visibleTicket(key,principal).getId(),principal.userId());}
    @Transactional public void follow(String key, AuthPrincipal principal){Ticket ticket=visibleTicket(key,principal); AppUser actor=users.getReferenceById(principal.userId()); if(!followers.existsByIdTicketIdAndIdUserId(ticket.getId(),principal.userId())) { followers.save(new TicketFollower(ticket,actor)); notifications.save(new Notification(actor,ticket,actor,"TICKET_FOLLOWED","Following ticket",ticket.getTicketKey()+" — you are now following this ticket.")); }}
    @Transactional public void unfollow(String key, AuthPrincipal principal){Ticket ticket=visibleTicket(key,principal); followers.deleteByIdTicketIdAndIdUserId(ticket.getId(),principal.userId());}

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(Long recipientId){
        return notifications.findTop100ByRecipientIdOrderByCreatedAtDesc(recipientId).stream().map(NotificationResponse::from).toList();
    }
    @Transactional(readOnly = true)
    public long unreadCount(Long recipientId){return notifications.countByRecipientIdAndReadAtIsNull(recipientId);}
    @Transactional
    public void markRead(Long id, Long recipientId){notifications.findByIdAndRecipientId(id, recipientId).ifPresent(Notification::markRead);}
    @Transactional
    public void markAllRead(Long recipientId){notifications.findTop100ByRecipientIdOrderByCreatedAtDesc(recipientId).stream().filter(n->n.getReadAt()==null).forEach(Notification::markRead);}

    /** Creates one notification for each newly assigned person or team member. */
    @Transactional
    public void notifyNewAssignments(Ticket ticket, Long actorId, Set<Long> previousRecipients){
        Set<Long> recipients = recipientIds(ticket);
        recipients.removeAll(previousRecipients == null ? Set.of() : previousRecipients);
        recipients.remove(actorId);
        if (recipients.isEmpty()) return;
        List<AppUser> assigned = users.findAllById(recipients);
        AppUser actor = actorId == null ? null : users.findById(actorId).orElse(null);
        List<Notification> created = assigned.stream().map(user -> new Notification(user, ticket, actor, "TICKET_ASSIGNED",
                "Ticket assigned to you", ticket.getTicketKey()+" — "+ticket.getTitle()+" was assigned to you.")).toList();
        notifications.saveAll(created);
    }
    @Transactional
    public void notifyUpdate(Ticket ticket, Long actorId, String action){
        if (ticket == null) return;
        Set<Long> recipients = recipientIds(ticket); recipients.remove(actorId);
        if (recipients.isEmpty()) return;
        Event event = eventFor(action);
        AppUser actor = actorId == null ? null : users.findById(actorId).orElse(null);
        String actorName = actor == null ? "Someone" : actor.getDisplayName();
        List<Notification> created = users.findAllById(recipients).stream().map(user -> new Notification(user, ticket, actor, event.type,
                event.title, actorName+" — "+event.description+" on "+ticket.getTicketKey()+" — "+ticket.getTitle()+".")).toList();
        notifications.saveAll(created);
    }
    private Event eventFor(String action){
        if ("COMMENT_ADDED".equals(action)) return new Event("COMMENT_ADDED", "New comment", "added a comment");
        if (action.contains("STATUS") || action.contains("WORKFLOW") || action.contains("CLIENT_ACCEPT") || action.contains("CLIENT_REJECT")) return new Event("WORKFLOW_UPDATED", "Workflow updated", "updated the workflow");
        if (action.contains("PROPOSAL")) return new Event("PROPOSAL_UPDATED", "Proposal updated", "updated a proposal");
        if (action.contains("ASSIGNEE") || action.contains("ASSIGN")) return new Event("ASSIGNMENT_CHANGED", "Assignment changed", "changed the assignment");
        return new Event("TICKET_UPDATED", "Ticket details changed", "updated the ticket details");
    }
    private record Event(String type, String title, String description) {}
    public Set<Long> recipientIds(Ticket ticket){
        Set<Long> result = new LinkedHashSet<>();
        if(ticket.getTicketLead()!=null) result.add(ticket.getTicketLead().getId());
        ticket.getDevelopers().forEach(user -> result.add(user.getId()));
        if(ticket.getResolvedApprover()!=null) result.add(ticket.getResolvedApprover().getId());
        for(DeveloperTeam team: ticket.getTeams()) team.getMembers().forEach(user -> result.add(user.getId()));
        result.addAll(followers.findUserIdsByTicketId(ticket.getId()));
        return result;
    }
    private Ticket visibleTicket(String key, AuthPrincipal principal){
        if(principal.party()==Responsibility.CLIENT) return tickets.findByTicketKeyAndOrganizationId(key,principal.organizationId()).orElseThrow(() -> com.ticketflow1.ticketing.common.ApiException.notFound("Ticket not found: "+key));
        return tickets.findByTicketKey(key).orElseThrow(() -> com.ticketflow1.ticketing.common.ApiException.notFound("Ticket not found: "+key));
    }
    public record NotificationResponse(Long id, String eventType, String title, String message, String ticketKey,
            String ticketTitle, Long actorId, String actorDisplayName, boolean read, java.time.Instant createdAt){
        static NotificationResponse from(Notification n){return new NotificationResponse(n.getId(),n.getEventType(),n.getTitle(),n.getMessage(),
                n.getTicket()==null?null:n.getTicket().getTicketKey(),n.getTicket()==null?null:n.getTicket().getTitle(),n.getActor()==null?null:n.getActor().getId(),n.getActor()==null?null:n.getActor().getDisplayName(),n.getReadAt()!=null,n.getCreatedAt());}
    }
}
