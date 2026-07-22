package com.ticketflow1.ticketing.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    boolean existsByNameIgnoreCase(String name);
    Optional<Organization> findByNameIgnoreCase(String name);
    List<Organization> findByActiveTrueOrderByNameAsc();
}
