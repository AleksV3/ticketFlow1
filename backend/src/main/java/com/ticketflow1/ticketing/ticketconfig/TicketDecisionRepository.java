package com.ticketflow1.ticketing.ticketconfig;
import java.util.List; import org.springframework.data.jpa.repository.JpaRepository;
public interface TicketDecisionRepository extends JpaRepository<TicketDecision,Long>{List<TicketDecision> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);}
