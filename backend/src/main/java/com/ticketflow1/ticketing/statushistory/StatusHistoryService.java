package com.ticketflow1.ticketing.statushistory;

import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.user.AppUserRepository;
import com.ticketflow1.ticketing.workflow.WorkflowState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatusHistoryService {

    private final StatusHistoryRepository statusHistoryRepository;
    private final AppUserRepository appUserRepository;

    public StatusHistoryService(StatusHistoryRepository statusHistoryRepository,
            AppUserRepository appUserRepository) {
        this.statusHistoryRepository = statusHistoryRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public StatusHistory record(Ticket ticket, WorkflowState fromState, WorkflowState toState, Long changedById) {
        StatusHistory statusHistory = new StatusHistory(
                ticket,
                fromState,
                toState,
                appUserRepository.getReferenceById(changedById));
        return statusHistoryRepository.save(statusHistory);
    }
}
