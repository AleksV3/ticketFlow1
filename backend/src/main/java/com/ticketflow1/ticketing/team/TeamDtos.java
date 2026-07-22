package com.ticketflow1.ticketing.team;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class TeamDtos {
    private TeamDtos() {}
    public record SaveTeamRequest(String name,String description,Long leaderId,Set<Long> memberIds,List<String> ticketKeys) {}
    public record ReorderTicketsRequest(List<String> ticketKeys) {}
    public record Person(Long id,String name,String party,String organizationName) {}
    public record TicketRef(String ticketKey,String title,String status,String type,String subtype,String severity,String priority,String responsibility,String organizationName,String parentTicketKey,String targetUserDisplaySnapshot,List<String> allowedTransitions) {}
    public record Options(List<Person> people,List<TicketRef> tickets) {}
    public record TeamResponse(Long id,String name,String description,Person leader,List<Person> members,List<TicketRef> tickets,Long createdById,boolean editable,Instant updatedAt) {
        static Person person(com.ticketflow1.ticketing.user.AppUser user){return new Person(user.getId(),user.getDisplayName(),user.getParty().name(),user.getOrganization()==null?null:user.getOrganization().getName());}
        static TicketRef ticket(com.ticketflow1.ticketing.ticket.Ticket t,java.util.function.Function<com.ticketflow1.ticketing.ticket.Ticket,List<String>> transitions){return new TicketRef(t.getTicketKey(),t.getTitle(),t.getCurrentState().getKey(),t.getTicketType().getKey(),t.getSubtype()==null?null:t.getSubtype().getKey(),t.getSeverity()==null?null:t.getSeverity().name(),t.getPriority().name(),t.getCurrentResponsibility().name(),t.getOrganization().getName(),t.getParentTicket()==null?null:t.getParentTicket().getTicketKey(),t.getTargetUserDisplaySnapshot(),transitions.apply(t));}
        static TeamResponse from(DeveloperTeam team,boolean editable,java.util.function.Predicate<com.ticketflow1.ticketing.ticket.Ticket> visible,java.util.function.Function<com.ticketflow1.ticketing.ticket.Ticket,List<String>> transitions){return new TeamResponse(team.getId(),team.getName(),team.getDescription(),person(team.getLeader()),team.getMembers().stream().map(TeamResponse::person).sorted(java.util.Comparator.comparing(Person::name)).toList(),team.getTickets().stream().filter(java.util.Objects::nonNull).filter(visible).map(t->ticket(t,transitions)).toList(),team.getCreatedBy().getId(),editable,team.getUpdatedAt());}
    }
}
