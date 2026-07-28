package com.ticketflow1.ticketing.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Replaces an administrator-provided one-time password after sign-in. */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 12, max = 100) String newPassword) {
}
