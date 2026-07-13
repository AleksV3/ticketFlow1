package com.ticketflow1.ticketing.comment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    List<Comment> findByTicketIdAndVisibilityOrderByCreatedAtAsc(Long ticketId, CommentVisibility visibility);
}
