package com.ticketflow1.ticketing.proposal;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChangeProposalRepository extends JpaRepository<ChangeProposal, Long> {

    Optional<ChangeProposal> findFirstByTicketIdOrderByCreatedAtDescIdDesc(Long ticketId);

    boolean existsByTicketIdAndStatus(Long ticketId, ProposalStatus status);
}
