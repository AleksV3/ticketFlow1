package com.ticketflow1.ticketing.statushistory.dto;

import com.ticketflow1.ticketing.statushistory.StatusHistory;
import java.time.Instant;

public record StatusHistoryResponse(
        Long id,
        String fromStatus,
        String toStatus,
        UserRef changedBy,
        Instant createdAt) {

    public static StatusHistoryResponse from(StatusHistory statusHistory) {
        return new StatusHistoryResponse(
                statusHistory.getId(),
                statusHistory.getFromState() == null ? null : statusHistory.getFromState().getKey(),
                statusHistory.getToState().getKey(),
                UserRef.from(statusHistory.getChangedBy()),
                statusHistory.getCreatedAt());
    }

    public record UserRef(Long id, String displayName) {
        public static UserRef from(com.ticketflow1.ticketing.user.AppUser user) {
            return new UserRef(user.getId(), user.getDisplayName());
        }
    }
}
