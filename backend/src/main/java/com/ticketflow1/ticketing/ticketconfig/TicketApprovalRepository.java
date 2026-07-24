package com.ticketflow1.ticketing.ticketconfig;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketApprovalRepository extends JpaRepository<TicketApproval, Long> {
    Optional<TicketApproval> findByTicketIdAndStatus(Long ticketId, TicketApprovalStatus status);
}

