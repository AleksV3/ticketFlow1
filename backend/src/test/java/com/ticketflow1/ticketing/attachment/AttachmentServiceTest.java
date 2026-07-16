package com.ticketflow1.ticketing.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketflow1.ticketing.attachment.dto.CreateAttachmentRequest;
import com.ticketflow1.ticketing.audit.AuditAction;
import com.ticketflow1.ticketing.audit.AuditService;
import com.ticketflow1.ticketing.auth.AuthPrincipal;
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
class AttachmentServiceTest {
    private static final long MAX_SIZE = 1000L;

    @Mock private AttachmentRepository attachmentRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private AuditService auditService;

    private AttachmentService attachmentService;
    private AuthPrincipal principal;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        attachmentService = new AttachmentService(attachmentRepository, ticketRepository,
                appUserRepository, auditService, MAX_SIZE, "./target/test-attachments");
        principal = new AuthPrincipal(7L, Responsibility.CLIENT, 3L, Set.of("TICKET_UPDATE"));
        ticket = mock(Ticket.class);
    }

    @Test
    void listUsesOrganizationScopedTicketLookup() {
        Attachment attachment = attachment(8L, "screen.png", "image/png", 500L);
        when(ticket.getId()).thenReturn(42L);
        when(ticketRepository.findByTicketKeyAndOrganizationId("TF-1000", 3L))
                .thenReturn(Optional.of(ticket));
        when(attachmentRepository.findByTicketIdOrderByCreatedAtAsc(42L)).thenReturn(List.of(attachment));

        var response = attachmentService.list("TF-1000", principal);

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.fileName()).isEqualTo("screen.png");
            assertThat(item.sizeBytes()).isEqualTo(500L);
        });
    }

    @Test
    void createRejectsMetadataLargerThanConfiguredLimit() {
        when(ticketRepository.findByTicketKeyAndOrganizationId("TF-1000", 3L))
                .thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> attachmentService.create("TF-1000",
                new CreateAttachmentRequest("large.zip", "application/zip", 1001L), principal))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getErrorCode())
                .isEqualTo("VALIDATION_FAILED");
        verify(attachmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createWritesPrivacySafeAuditMetadata() {
        AppUser uploader = mock(AppUser.class);
        Attachment saved = attachment(8L, "screen.png", "image/png", 500L);
        when(ticketRepository.findByTicketKeyAndOrganizationId("TF-1000", 3L))
                .thenReturn(Optional.of(ticket));
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(uploader));
        when(uploader.getId()).thenReturn(7L);
        when(attachmentRepository.saveAndFlush(any(Attachment.class))).thenReturn(saved);

        var response = attachmentService.create("TF-1000",
                new CreateAttachmentRequest(" screen.png ", "image/png", 500L), principal);

        assertThat(response.id()).isEqualTo(8L);
        verify(auditService).record(ticket, 7L, AuditAction.ATTACHMENT_ADDED,
                "attachment", "image/png", "8");
    }

    private Attachment attachment(Long id, String fileName, String contentType, long sizeBytes) {
        Attachment attachment = mock(Attachment.class);
        AppUser uploader = mock(AppUser.class);
        when(attachment.getId()).thenReturn(id);
        when(attachment.getUploadedBy()).thenReturn(uploader);
        when(uploader.getId()).thenReturn(7L);
        when(uploader.getDisplayName()).thenReturn("Test User");
        when(attachment.getFileName()).thenReturn(fileName);
        when(attachment.getContentType()).thenReturn(contentType);
        when(attachment.getSizeBytes()).thenReturn(sizeBytes);
        when(attachment.getCreatedAt()).thenReturn(Instant.parse("2026-07-13T10:00:00Z"));
        return attachment;
    }
}
