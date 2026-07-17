package com.ticketflow1.ticketing.attachment;

import com.ticketflow1.ticketing.attachment.dto.AttachmentResponse;
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
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.util.UUID;

/**
 * Manages attachment metadata and file storage for tickets.
 *
 * The service validates file uploads, stores bytes on disk, keeps metadata in
 * the database, returns safe download responses, and deletes both the row and
 * the file when an attachment is removed.
 */
@Service
public class AttachmentService {
    private final AttachmentRepository attachmentRepository;
    private final TicketRepository ticketRepository;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;
    private final long maxSizeBytes;
    private final Path storageRoot;

    public AttachmentService(AttachmentRepository attachmentRepository, TicketRepository ticketRepository,
            AppUserRepository appUserRepository, AuditService auditService,
            @Value("${app.attachments.max-size-bytes:104857600}") long maxSizeBytes,
            @Value("${app.attachments.storage-directory:./data/attachments}") String storageDirectory) {
        this.attachmentRepository = attachmentRepository;
        this.ticketRepository = ticketRepository;
        this.appUserRepository = appUserRepository;
        this.auditService = auditService;
        this.maxSizeBytes = maxSizeBytes;
        this.storageRoot = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    /**
     * Uploads a file, stores it on disk, and records attachment metadata.
     */
    @Transactional
    public AttachmentResponse upload(String ticketKey, MultipartFile file, AuthPrincipal principal) {
        if (file.isEmpty() || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw ApiException.validation("Choose a non-empty file.");
        }
        if (file.getSize() > maxSizeBytes) throw ApiException.validation("File exceeds the configured limit of " + maxSizeBytes + " bytes.");
        Ticket ticket = findVisibleTicket(ticketKey, principal);
        AppUser uploader = appUserRepository.findById(principal.userId()).orElseThrow(() -> ApiException.notFound("Current user no longer exists."));
        String fileName = Path.of(file.getOriginalFilename()).getFileName().toString();
        String contentType = file.getContentType() == null || !file.getContentType().contains("/") ? "application/octet-stream" : file.getContentType();
        String storedName = UUID.randomUUID() + "-" + fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        Path target = storageRoot.resolve(storedName).normalize();
        if (!target.startsWith(storageRoot)) throw ApiException.validation("Invalid file name.");
        try { Files.createDirectories(storageRoot); Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException exception) { throw new IllegalStateException("Could not store attachment.", exception); }
        try {
            Attachment attachment = new Attachment(ticket, uploader, fileName, contentType, file.getSize());
            attachment.storeAt(storedName);
            Attachment saved = attachmentRepository.saveAndFlush(attachment);
            auditService.record(ticket, uploader.getId(), AuditAction.ATTACHMENT_ADDED, "attachment", contentType, saved.getId().toString());
            return AttachmentResponse.from(saved);
        } catch (RuntimeException exception) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            throw exception;
        }
    }

    /**
     * Streams attachment content back to the client when the file is present.
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> download(String ticketKey, Long attachmentId, AuthPrincipal principal) {
        Ticket ticket = findVisibleTicket(ticketKey, principal);
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .filter(value -> value.getTicket().getId().equals(ticket.getId()))
                .orElseThrow(() -> ApiException.notFound("Attachment not found: " + attachmentId));
        if (attachment.getStoragePath() == null) throw ApiException.notFound("This legacy attachment is metadata-only.");
        Path path = storageRoot.resolve(attachment.getStoragePath()).normalize();
        if (!path.startsWith(storageRoot) || !Files.isRegularFile(path)) throw ApiException.notFound("Attachment content is unavailable.");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(attachment.getFileName()).build().toString())
                .contentLength(attachment.getSizeBytes()).body(new FileSystemResource(path));
    }

    /**
     * Lists attachments in creation order for the visible ticket.
     */
    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(String ticketKey, AuthPrincipal principal) {
        Ticket ticket = findVisibleTicket(ticketKey, principal);
        return attachmentRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()).stream()
                .map(AttachmentResponse::from).toList();
    }

    /**
     * Creates metadata-only attachments used for references or external files.
     */
    @Transactional
    public AttachmentResponse create(String ticketKey, CreateAttachmentRequest request, AuthPrincipal principal) {
        Ticket ticket = findVisibleTicket(ticketKey, principal);
        if (request.sizeBytes() > maxSizeBytes) {
            throw ApiException.validation("sizeBytes exceeds the configured limit of " + maxSizeBytes + ".");
        }
        AppUser uploader = appUserRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("Current user no longer exists."));
        Attachment saved = attachmentRepository.saveAndFlush(new Attachment(ticket, uploader,
                request.fileName().trim(), request.contentType().trim(), request.sizeBytes()));
        auditService.record(ticket, uploader.getId(), AuditAction.ATTACHMENT_ADDED,
                "attachment", request.contentType().trim(), saved.getId().toString());
        return AttachmentResponse.from(saved);
    }

    /**
     * Removes the attachment row and deletes the stored file when one exists.
     */
    @Transactional
    public void remove(String ticketKey, Long attachmentId, AuthPrincipal principal) {
        Ticket ticket = findVisibleTicket(ticketKey, principal);
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .filter(value -> value.getTicket().getId().equals(ticket.getId()))
                .orElseThrow(() -> ApiException.notFound("Attachment not found: " + attachmentId));
        if (attachment.getStoragePath() != null) {
            Path path = storageRoot.resolve(attachment.getStoragePath()).normalize();
            if (path.startsWith(storageRoot)) {
                try { Files.deleteIfExists(path); }
                catch (IOException exception) { throw new IllegalStateException("Could not remove attachment file.", exception); }
            }
        }
        attachmentRepository.delete(attachment);
    }

    private Ticket findVisibleTicket(String ticketKey, AuthPrincipal principal) {
        if (principal.party() == Responsibility.CLIENT) {
            return ticketRepository.findByTicketKeyAndOrganizationId(ticketKey, principal.organizationId())
                    .orElseThrow(() -> ApiException.notFound("Ticket not found: " + ticketKey));
        }
        return ticketRepository.findByTicketKey(ticketKey)
                .orElseThrow(() -> ApiException.notFound("Ticket not found: " + ticketKey));
    }
}
