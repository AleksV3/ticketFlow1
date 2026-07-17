package com.ticketflow1.ticketing.attachment;

import com.ticketflow1.ticketing.attachment.dto.AttachmentResponse;
import com.ticketflow1.ticketing.attachment.dto.CreateAttachmentRequest;
import com.ticketflow1.ticketing.auth.AuthPrincipal;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;

/**
 * Attachment API for a single ticket.
 *
 * The controller only handles HTTP wiring; storage, validation, and cleanup
 * are implemented in {@link AttachmentService}.
 */
@RestController
@RequestMapping("/api/tickets/{ticketKey}/attachments")
public class AttachmentController {
    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) { this.attachmentService = attachmentService; }

    /**
     * Lists attachment metadata for the ticket.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('TICKET_READ')")
    public List<AttachmentResponse> list(@PathVariable String ticketKey,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return attachmentService.list(ticketKey, principal);
    }

    /**
     * Creates a metadata-only attachment entry.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TICKET_UPDATE')")
    public AttachmentResponse create(@PathVariable String ticketKey,
            @Valid @RequestBody CreateAttachmentRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return attachmentService.create(ticketKey, request, principal);
    }

    /**
     * Uploads a file and stores the bytes on disk.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TICKET_UPDATE')")
    public AttachmentResponse upload(@PathVariable String ticketKey,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return attachmentService.upload(ticketKey, file, principal);
    }

    /**
     * Downloads the stored attachment content.
     */
    @GetMapping("/{attachmentId}/content")
    @PreAuthorize("hasAuthority('TICKET_READ')")
    public ResponseEntity<Resource> download(@PathVariable String ticketKey,
            @PathVariable Long attachmentId, @AuthenticationPrincipal AuthPrincipal principal) {
        return attachmentService.download(ticketKey, attachmentId, principal);
    }

    /**
     * Removes the attachment and its stored file when present.
     */
    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('TICKET_UPDATE')")
    public void remove(@PathVariable String ticketKey, @PathVariable Long attachmentId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        attachmentService.remove(ticketKey, attachmentId, principal);
    }
}
