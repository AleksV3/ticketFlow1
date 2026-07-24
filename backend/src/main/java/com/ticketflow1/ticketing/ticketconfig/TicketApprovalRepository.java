package com.ticketflow1.ticketing.ticketconfig;

import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketApprovalRepository extends JpaRepository<TicketApproval, Long> {
    Optional<TicketApproval> findByTicketIdAndStatus(Long ticketId, TicketApprovalStatus status);

    @EntityGraph(attributePaths = {
            "ticket", "assignedApprover", "assignedTeam",
            "assignedTeam.leader", "assignedTeam.members"
    })
    List<TicketApproval> findByStatus(TicketApprovalStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select approval from TicketApproval approval
            where approval.ticket.id = :ticketId and approval.status = :status
            """)
    Optional<TicketApproval> findForUpdate(
            @Param("ticketId") Long ticketId,
            @Param("status") TicketApprovalStatus status);
}
