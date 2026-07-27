package com.ticketflow1.ticketing.notification;

import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TicketFollowerRepository extends JpaRepository<TicketFollower, TicketFollower.Id> {
    boolean existsByIdTicketIdAndIdUserId(Long ticketId, Long userId);
    void deleteByIdTicketIdAndIdUserId(Long ticketId, Long userId);
    @Query("select f.id.userId from TicketFollower f where f.id.ticketId = :ticketId") Set<Long> findUserIdsByTicketId(Long ticketId);
}
