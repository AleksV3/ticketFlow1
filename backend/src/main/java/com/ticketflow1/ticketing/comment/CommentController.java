package com.ticketflow1.ticketing.comment;

import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.comment.dto.CommentResponse;
import com.ticketflow1.ticketing.comment.dto.CreateCommentRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Comment API for a ticket.
 *
 * The controller keeps HTTP concerns thin and delegates visibility rules and
 * SLA side effects to {@link CommentService}.
 */
@RestController
@RequestMapping("/api/tickets/{ticketKey}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /**
     * Lists comments visible to the current user.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('TICKET_READ')")
    public List<CommentResponse> list(@PathVariable String ticketKey,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return commentService.list(ticketKey, principal);
    }

    /**
     * Creates a new public or internal comment depending on permissions.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COMMENT_PUBLIC_WRITE')")
    public CommentResponse create(@PathVariable String ticketKey,
            @Valid @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return commentService.create(ticketKey, request, principal);
    }
}
