package com.ticketflow1.ticketing.notification;

import com.ticketflow1.ticketing.ticket.Ticket;
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
    public NotificationService(NotificationRepository notifications, AppUserRepository users){this.notifications=notifications;this.users=users;}

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
        List<Notification> created = assigned.stream().map(user -> new Notification(user, ticket, "TICKET_ASSIGNED",
                "Ticket assigned to you", ticket.getTicketKey()+" — "+ticket.getTitle()+" was assigned to you.")).toList();
        notifications.saveAll(created);
    }
    @Transactional
    public void notifyUpdate(Ticket ticket, Long actorId, String action){
        if (ticket == null) return;
        Set<Long> recipients = recipientIds(ticket); recipients.remove(actorId);
        if (recipients.isEmpty()) return;
        String label = action.replace('_', ' ').toLowerCase(Locale.ROOT);
        List<Notification> created = users.findAllById(recipients).stream().map(user -> new Notification(user, ticket, "TICKET_UPDATED",
                "Ticket updated", ticket.getTicketKey()+" — "+label+".")).toList();
        notifications.saveAll(created);
    }
    public Set<Long> recipientIds(Ticket ticket){
        Set<Long> result = new LinkedHashSet<>();
        if(ticket.getTicketLead()!=null) result.add(ticket.getTicketLead().getId());
        ticket.getDevelopers().forEach(user -> result.add(user.getId()));
        if(ticket.getResolvedApprover()!=null) result.add(ticket.getResolvedApprover().getId());
        for(DeveloperTeam team: ticket.getTeams()) team.getMembers().forEach(user -> result.add(user.getId()));
        return result;
    }
    public record NotificationResponse(Long id, String eventType, String title, String message, String ticketKey,
            String ticketTitle, boolean read, java.time.Instant createdAt){
        static NotificationResponse from(Notification n){return new NotificationResponse(n.getId(),n.getEventType(),n.getTitle(),n.getMessage(),
                n.getTicket()==null?null:n.getTicket().getTicketKey(),n.getTicket()==null?null:n.getTicket().getTitle(),n.getReadAt()!=null,n.getCreatedAt());}
    }
}
