package com.ticketflow1.ticketing.ticket;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    List<Ticket> findByOrganizationId(Long organizationId);

    Optional<Ticket> findByTicketKey(String ticketKey);

    Optional<Ticket> findByTicketKeyAndOrganizationId(String ticketKey, Long organizationId);
}
