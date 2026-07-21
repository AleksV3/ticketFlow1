package com.ticketflow1.ticketing.ticket.dto;

import jakarta.validation.constraints.NotBlank;

public record CorrectionReturnRequest(@NotBlank String reason) { }
