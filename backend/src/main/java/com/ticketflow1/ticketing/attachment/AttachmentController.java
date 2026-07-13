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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets/{ticketKey}/attachments")
public class AttachmentController {
    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) { this.attachmentService = attachmentService; }

    @GetMapping
    @PreAuthorize("hasAuthority('TICKET_READ')")
    public List<AttachmentResponse> list(@PathVariable String ticketKey,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return attachmentService.list(ticketKey, principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TICKET_UPDATE')")
    public AttachmentResponse create(@PathVariable String ticketKey,
            @Valid @RequestBody CreateAttachmentRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return attachmentService.create(ticketKey, request, principal);
    }
}
