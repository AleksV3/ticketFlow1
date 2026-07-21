package com.ticketflow1.ticketing.team;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeveloperTeamRepository extends JpaRepository<DeveloperTeam,Long> {
    List<DeveloperTeam> findAllByOrderByNameAsc();
    List<DeveloperTeam> findDistinctByLeaderIdOrMembersIdOrderByNameAsc(Long leaderId,Long memberId);
    @Modifying
    @Query(value="UPDATE developer_team_ticket SET sort_order=-sort_order-1 WHERE team_id=:teamId",nativeQuery=true)
    int parkTicketPositions(@Param("teamId")Long teamId);
    @Modifying
    @Query(value="UPDATE developer_team_ticket SET sort_order=:position WHERE team_id=:teamId AND ticket_id=:ticketId",nativeQuery=true)
    int updateTicketPosition(@Param("teamId")Long teamId,@Param("ticketId")Long ticketId,@Param("position")int position);
}
