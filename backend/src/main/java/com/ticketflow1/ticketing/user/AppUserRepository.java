package com.ticketflow1.ticketing.user;

import java.util.Optional;
import java.util.List;
import com.ticketflow1.ticketing.ticket.Responsibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);
    List<AppUser> findByActiveTrueAndPartyOrderByDisplayNameAsc(Responsibility party);
    List<AppUser> findByActiveTrueOrderByDisplayNameAsc();
    List<AppUser> findByActiveTrueAndOrganizationIdOrderByDisplayNameAsc(Long organizationId);
    @Query("select u from AppUser u where u.active=true and u.organization.id=:organizationId and " +
            "(lower(u.displayName) like lower(concat('%',:query,'%')) or lower(u.email) like lower(concat('%',:query,'%'))) " +
            "order by u.displayName asc,u.id asc")
    List<AppUser> searchActiveDirectory(@Param("organizationId") Long organizationId,@Param("query") String query,Pageable pageable);
}
