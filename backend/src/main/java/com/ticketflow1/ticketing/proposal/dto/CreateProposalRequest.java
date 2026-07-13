package com.ticketflow1.ticketing.proposal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateProposalRequest(@NotBlank String description, LocalDate estimatedDeliveryDate,
        @Size(max = 100) String effortEstimate) { }
