package com.ticketflow1.ticketing.attachment;

import com.ticketflow1.ticketing.common.Auditable;
import com.ticketflow1.ticketing.ticket.Ticket;
import com.ticketflow1.ticketing.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "attachment")
public class Attachment extends Auditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ticket_id") private Ticket ticket;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "uploaded_by_id") private AppUser uploadedBy;
    @Column(name = "file_name", nullable = false, length = 255) private String fileName;
    @Column(name = "content_type", nullable = false, length = 100) private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(name = "storage_path", length = 500) private String storagePath;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected Attachment() { }

    public Attachment(Ticket ticket, AppUser uploadedBy, String fileName, String contentType, long sizeBytes) {
        this.ticket = ticket;
        this.uploadedBy = uploadedBy;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    public Long getId() { return id; }
    public AppUser getUploadedBy() { return uploadedBy; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
}
