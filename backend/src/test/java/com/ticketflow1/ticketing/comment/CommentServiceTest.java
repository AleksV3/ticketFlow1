package com.ticketflow1.ticketing.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketflow1.ticketing.audit.AuditAction;
import com.ticketflow1.ticketing.audit.AuditService;
import com.ticketflow1.ticketing.auth.AuthPrincipal;
import com.ticketflow1.ticketing.comment.dto.CreateCommentRequest;
import com.ticketflow1.ticketing.common.ApiException;
import com.ticketflow1.ticketing.ticket.Responsibility;
import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.ticket.TicketRepository;
import com.ticketflow1.ticketing.user.AppUser;
import com.ticketflow1.ticketing.user.AppUserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private AuditService auditService;
    @Mock private com.ticketflow1.ticketing.sla.SlaCalculator slaCalculator;

    private CommentService commentService;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(commentRepository, ticketRepository, appUserRepository, auditService,
                slaCalculator, java.time.Clock.systemUTC());
        ticket = mock(Ticket.class);
    }

    @Test
    void listWithoutInternalReadReturnsOnlyPublicComments() {
        AuthPrincipal principal = principal(Set.of("TICKET_READ"));
        Comment publicComment = comment(1L, CommentVisibility.PUBLIC, "Visible");
        when(ticket.getId()).thenReturn(42L);
        when(ticketRepository.findByTicketKeyAndOrganizationId("TF-1000", 3L)).thenReturn(Optional.of(ticket));
        when(commentRepository.findByTicketIdAndVisibilityOrderByCreatedAtAsc(42L, CommentVisibility.PUBLIC))
                .thenReturn(List.of(publicComment));

        var response = commentService.list("TF-1000", principal);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().body()).isEqualTo("Visible");
        verify(commentRepository, never()).findByTicketIdOrderByCreatedAtAsc(42L);
    }

    @Test
    void listWithInternalReadReturnsAllComments() {
        AuthPrincipal principal = internalPrincipal(Set.of("TICKET_READ", CommentService.INTERNAL_READ));
        Comment publicComment = comment(1L, CommentVisibility.PUBLIC, "Public");
        Comment internalComment = comment(2L, CommentVisibility.INTERNAL, "Internal");
        when(ticket.getId()).thenReturn(42L);
        when(ticketRepository.findByTicketKey("TF-1000")).thenReturn(Optional.of(ticket));
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(42L))
                .thenReturn(List.of(publicComment, internalComment));

        assertThat(commentService.list("TF-1000", principal)).hasSize(2);
    }

    @Test
    void createInternalWithoutPermissionIsForbiddenBeforePersistence() {
        AuthPrincipal principal = principal(Set.of("COMMENT_PUBLIC_WRITE"));
        when(ticketRepository.findByTicketKeyAndOrganizationId("TF-1000", 3L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> commentService.create("TF-1000",
                new CreateCommentRequest("Secret", CommentVisibility.INTERNAL), principal))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getErrorCode())
                .isEqualTo("FORBIDDEN");

        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createPublicTrimsBodyAndAuditsOnlyIdAndVisibility() {
        AuthPrincipal principal = principal(Set.of("COMMENT_PUBLIC_WRITE"));
        AppUser author = mock(AppUser.class);
        Comment saved = comment(9L, CommentVisibility.PUBLIC, "Hello");
        when(ticketRepository.findByTicketKeyAndOrganizationId("TF-1000", 3L)).thenReturn(Optional.of(ticket));
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(author));
        when(author.getId()).thenReturn(7L);
        when(commentRepository.saveAndFlush(any(Comment.class))).thenReturn(saved);

        var response = commentService.create("TF-1000",
                new CreateCommentRequest("  Hello  ", CommentVisibility.PUBLIC), principal);

        assertThat(response.id()).isEqualTo(9L);
        verify(auditService).record(ticket, 7L, AuditAction.COMMENT_ADDED,
                "comment", "PUBLIC", "9");
    }

    private AuthPrincipal principal(Set<String> permissions) {
        return new AuthPrincipal(7L, Responsibility.CLIENT, 3L, permissions);
    }

    private AuthPrincipal internalPrincipal(Set<String> permissions) {
        return new AuthPrincipal(7L, Responsibility.TICKETFLOW1, null, permissions);
    }

    private Comment comment(Long id, CommentVisibility visibility, String body) {
        Comment comment = mock(Comment.class);
        AppUser author = mock(AppUser.class);
        when(comment.getId()).thenReturn(id);
        when(comment.getAuthor()).thenReturn(author);
        when(author.getId()).thenReturn(7L);
        when(author.getDisplayName()).thenReturn("Test User");
        when(comment.getBody()).thenReturn(body);
        when(comment.getVisibility()).thenReturn(visibility);
        when(comment.getCreatedAt()).thenReturn(Instant.parse("2026-07-13T10:00:00Z"));
        return comment;
    }
}
