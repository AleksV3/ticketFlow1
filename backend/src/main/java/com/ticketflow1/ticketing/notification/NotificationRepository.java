package com.ticketflow1.ticketing.notification;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop100ByRecipientIdOrderByCreatedAtDesc(Long recipientId);
    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);
    long countByRecipientIdAndReadAtIsNull(Long recipientId);
}
