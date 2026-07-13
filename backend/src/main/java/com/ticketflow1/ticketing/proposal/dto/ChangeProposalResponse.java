package com.ticketflow1.ticketing.proposal.dto;

import com.ticketflow1.ticketing.proposal.ChangeProposal;
import java.time.Instant;
import java.time.LocalDate;

public record ChangeProposalResponse(Long id, Long ticketId, String ticketKey, String description,
        LocalDate estimatedDeliveryDate, String effortEstimate, String status, UserRef createdBy,
        UserRef decidedBy, Instant decidedAt, Instant createdAt, long version) {
    public static ChangeProposalResponse from(ChangeProposal p) {
        return new ChangeProposalResponse(p.getId(), p.getTicket().getId(), p.getTicket().getTicketKey(),
                p.getDescription(), p.getEstimatedDeliveryDate(), p.getEffortEstimate(), p.getStatus().name(),
                UserRef.from(p.getCreatedBy()), UserRef.from(p.getDecidedBy()), p.getDecidedAt(),
                p.getCreatedAt(), p.getVersion());
    }
    public record UserRef(Long id, String displayName) {
        static UserRef from(com.ticketflow1.ticketing.user.AppUser user) {
            return user == null ? null : new UserRef(user.getId(), user.getDisplayName());
        }
    }
}
