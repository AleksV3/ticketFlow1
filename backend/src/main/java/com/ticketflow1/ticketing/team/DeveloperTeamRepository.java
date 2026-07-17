package com.ticketflow1.ticketing.team;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeveloperTeamRepository extends JpaRepository<DeveloperTeam,Long> {
    List<DeveloperTeam> findAllByOrderByNameAsc();
}
