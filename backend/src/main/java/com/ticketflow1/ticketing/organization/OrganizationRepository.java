package com.ticketflow1.ticketing.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    boolean existsByNameIgnoreCase(String name);
    List<Organization> findByActiveTrueOrderByNameAsc();
}
