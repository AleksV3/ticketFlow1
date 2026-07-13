package com.ticketflow1.ticketing.comment.dto;

import com.ticketflow1.ticketing.comment.CommentVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotBlank @Size(max = 10000) String body,
        @NotNull CommentVisibility visibility) {
}
