package com.ticketflow1.ticketing.attachment.dto;

import com.ticketflow1.ticketing.attachment.Attachment;
import java.time.Instant;

public record AttachmentResponse(Long id, UserRef uploadedBy, String fileName,
        String contentType, long sizeBytes, Instant createdAt) {
    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(attachment.getId(),
                new UserRef(attachment.getUploadedBy().getId(), attachment.getUploadedBy().getDisplayName()),
                attachment.getFileName(), attachment.getContentType(), attachment.getSizeBytes(),
                attachment.getCreatedAt());
    }

    public record UserRef(Long id, String displayName) { }
}
