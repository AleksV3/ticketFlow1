package com.ticketflow1.ticketing.team;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class TeamDtos {
    private TeamDtos() {}
    public record SaveTeamRequest(String name,String description,Long leaderId,Set<Long> developerIds,Set<String> ticketKeys) {}
    public record Person(Long id,String name) {}
    public record TicketRef(String ticketKey,String title,String status) {}
    public record Options(List<Person> people,List<TicketRef> tickets) {}
    public record TeamResponse(Long id,String name,String description,Person leader,List<Person> developers,List<TicketRef> tickets,Long createdById,boolean editable,Instant updatedAt) {
        static TeamResponse from(DeveloperTeam team,boolean editable){return new TeamResponse(team.getId(),team.getName(),team.getDescription(),new Person(team.getLeader().getId(),team.getLeader().getDisplayName()),team.getDevelopers().stream().map(u->new Person(u.getId(),u.getDisplayName())).sorted(java.util.Comparator.comparing(Person::name)).toList(),team.getTickets().stream().map(t->new TicketRef(t.getTicketKey(),t.getTitle(),t.getCurrentState().getKey())).sorted(java.util.Comparator.comparing(TicketRef::ticketKey)).toList(),team.getCreatedBy().getId(),editable,team.getUpdatedAt());}
    }
}
