package com.ticketflow1.ticketing.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
