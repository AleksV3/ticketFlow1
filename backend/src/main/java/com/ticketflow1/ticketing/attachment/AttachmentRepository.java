package com.ticketflow1.ticketing.attachment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
