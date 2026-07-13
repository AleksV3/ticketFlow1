package com.ticketflow1.ticketing.comment.dto;

import com.ticketflow1.ticketing.comment.Comment;
import com.ticketflow1.ticketing.comment.CommentVisibility;
import java.time.Instant;

public record CommentResponse(
        Long id,
        AuthorRef author,
        String body,
        CommentVisibility visibility,
        Instant createdAt) {

    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                new AuthorRef(comment.getAuthor().getId(), comment.getAuthor().getDisplayName()),
                comment.getBody(),
                comment.getVisibility(),
                comment.getCreatedAt());
    }

    public record AuthorRef(Long id, String displayName) {
    }
}
