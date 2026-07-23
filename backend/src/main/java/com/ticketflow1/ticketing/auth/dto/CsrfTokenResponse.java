package com.ticketflow1.ticketing.auth.dto;

public record CsrfTokenResponse(String headerName, String token) {
}
