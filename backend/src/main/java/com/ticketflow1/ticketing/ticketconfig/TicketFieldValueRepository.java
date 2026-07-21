package com.ticketflow1.ticketing.ticketconfig;
import java.util.List; import org.springframework.data.jpa.repository.JpaRepository;
public interface TicketFieldValueRepository extends JpaRepository<TicketFieldValue,Long>{List<TicketFieldValue> findByTicketId(Long ticketId);}
