package com.ticketflow1.ticketing.user;

import java.util.Optional;
import java.util.List;
import com.ticketflow1.ticketing.ticket.Responsibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);
    List<AppUser> findByActiveTrueAndPartyOrderByDisplayNameAsc(Responsibility party);
    List<AppUser> findByActiveTrueOrderByDisplayNameAsc();
    List<AppUser> findByActiveTrueAndOrganizationIdOrderByDisplayNameAsc(Long organizationId);
}
