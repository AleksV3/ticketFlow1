package com.ticketflow1.ticketing.ticket;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.user.AppUser;
import com.ticketflow1.ticketing.user.AppUserRepository;
import com.ticketflow1.ticketing.team.DeveloperTeamRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Coordinates the short-lived administrator edit lock for a ticket. */
@Service
public class TicketEditLockService {
    private static final Duration TIMEOUT = Duration.ofMinutes(15);
    private final Map<String, Lock> locks = new ConcurrentHashMap<>();
    private final TicketRepository tickets;
    private final AppUserRepository users;
    private final DeveloperTeamRepository teams;
    private final Clock clock;

    public TicketEditLockService(TicketRepository tickets, AppUserRepository users,
            DeveloperTeamRepository teams, Clock clock) {
        this.tickets = tickets;
        this.users = users;
        this.teams = teams;
        this.clock = clock;
    }

    public Lock acquire(Ticket ticket, AuthPrincipal principal) {
        expire(ticket.getTicketKey());
        Instant now = clock.instant();
        Lock current = locks.get(ticket.getTicketKey());
        boolean admin = principal.hasPermission("USER_MANAGE");
        if (current != null && !current.ownerId().equals(principal.userId()) && !admin) {
            throw ApiException.conflict("This ticket is being edited by another administrator.");
        }
        Lock lock = new Lock(principal.userId(), admin, now.plus(TIMEOUT), Snapshot.capture(ticket));
        locks.put(ticket.getTicketKey(), lock);
        return lock;
    }

    public void release(Ticket ticket, AuthPrincipal principal) {
        Lock lock = locks.get(ticket.getTicketKey());
        if (lock != null && (lock.ownerId().equals(principal.userId()) || principal.hasPermission("USER_MANAGE"))) {
            locks.remove(ticket.getTicketKey());
        }
    }

    public void beforeMutation(Ticket ticket, AuthPrincipal principal) {
        expire(ticket.getTicketKey());
        Lock lock = locks.get(ticket.getTicketKey());
        if (lock != null && !lock.ownerId().equals(principal.userId()) && !principal.hasPermission("USER_MANAGE")) {
            throw ApiException.conflict("This ticket is being edited by another user until " + lock.expiresAt() + ".");
        }
    }

    private void expire(String key) {
        Lock lock = locks.get(key);
        if (lock == null || lock.expiresAt().isAfter(clock.instant())) return;
        tickets.findByTicketKey(key).ifPresent(ticket -> {
            lock.snapshot().restore(ticket, users, teams);
            tickets.save(ticket);
        });
        locks.remove(key, lock);
    }

    public record Lock(Long ownerId, boolean admin, Instant expiresAt, Snapshot snapshot) {}

    public record Snapshot(String title, String description, Priority priority, Severity severity,
            String assignedTeam, Long leadId, Set<Long> developerIds, Set<Long> teamIds) {
        static Snapshot capture(Ticket ticket) {
            return new Snapshot(ticket.getTitle(), ticket.getDescription(), ticket.getPriority(), ticket.getSeverity(),
                    ticket.getAssignedTeam(), ticket.getTicketLead() == null ? null : ticket.getTicketLead().getId(),
                    ticket.getDevelopers().stream().map(AppUser::getId).collect(java.util.stream.Collectors.toSet()),
                    ticket.getTeams().stream().map(team -> team.getId()).collect(java.util.stream.Collectors.toSet()));
        }
        void restore(Ticket ticket, AppUserRepository users, DeveloperTeamRepository teams) {
            ticket.setTitle(title); ticket.setDescription(description); ticket.setPriority(priority);
            ticket.setSeverity(severity); ticket.setAssignedTeam(assignedTeam);
            ticket.setTicketLead(leadId == null ? null : users.findById(leadId).orElse(null));
            ticket.replaceDevelopers(new java.util.LinkedHashSet<>(users.findAllById(developerIds)));
            ticket.replaceTeams(new java.util.LinkedHashSet<>(teams.findAllById(teamIds)));
        }
    }
}
