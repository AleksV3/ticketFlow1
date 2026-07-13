package com.ticketflow1.ticketing.attachment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateAttachmentRequest(
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "^[^\\s/]+/[^\\s/]+$", message = "must be a MIME-shaped value such as image/png")
        String contentType,
        @NotNull @PositiveOrZero Long sizeBytes) {
}
