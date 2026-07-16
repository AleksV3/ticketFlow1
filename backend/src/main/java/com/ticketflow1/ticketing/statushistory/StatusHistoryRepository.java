package com.ticketflow1.ticketing.statushistory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    List<StatusHistory> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
    boolean existsByFromStateIdOrToStateId(Long fromStateId, Long toStateId);
}
