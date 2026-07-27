package com.ticketflow1.ticketing.notification;

import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TicketFollowerRepository extends JpaRepository<TicketFollower, TicketFollower.Id> {
    boolean existsByIdTicketIdAndIdUserId(Long ticketId, Long userId);
    java.util.Optional<TicketFollower> findByIdTicketIdAndIdUserId(Long ticketId, Long userId);
    void deleteByIdTicketIdAndIdUserId(Long ticketId, Long userId);
    @Query("select f.id.userId from TicketFollower f where f.id.ticketId = :ticketId and f.muted = false") Set<Long> findUserIdsByTicketId(Long ticketId);
    @Query("select f.id.userId from TicketFollower f where f.id.ticketId = :ticketId and f.muted = true") Set<Long> findMutedUserIdsByTicketId(Long ticketId);
}
