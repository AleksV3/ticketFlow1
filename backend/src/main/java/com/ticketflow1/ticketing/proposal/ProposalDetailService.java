package com.ticketflow1.ticketing.proposal;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.proposal.dto.ChangeProposalResponse;
import com.ticketflow1.ticketing.ticket.Responsibility;
import com.ticketflow1.ticketing.ticket.Ticket;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProposalDetailService {
    private final ChangeProposalRepository repository;

    public ProposalDetailService(ChangeProposalRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public ProposalDetail detail(Ticket ticket, AuthPrincipal principal) {
        ChangeProposal latest = repository.findFirstByTicketIdOrderByCreatedAtDescIdDesc(ticket.getId())
                .orElse(null);
        List<String> commands = new ArrayList<>();
        if (ticket.getTicketType().isRequiresProposal()
                && "ANALYSIS".equals(ticket.getCurrentState().getKey())
                && principal.party() == Responsibility.TICKETFLOW1
                && principal.hasPermission("TICKET_TRANSITION")
                && (latest == null || latest.getStatus() != ProposalStatus.PENDING)) {
            commands.add("CREATE");
        }
        if (latest != null && latest.getStatus() == ProposalStatus.PENDING
                && "PROPOSAL".equals(ticket.getCurrentState().getKey())
                && principal.party() == Responsibility.CLIENT
                && ticket.getOrganization().getId().equals(principal.organizationId())
                && principal.hasPermission("PROPOSAL_APPROVE")) {
            commands.add("APPROVE");
            commands.add("REJECT");
        }
        return new ProposalDetail(latest == null ? null : ChangeProposalResponse.from(latest), List.copyOf(commands));
    }

    public record ProposalDetail(ChangeProposalResponse latestProposal, List<String> permittedCommands) { }
}
