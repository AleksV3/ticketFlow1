package com.ticketflow1.ticketing.workflow;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {

    List<TicketType> findByOrganizationId(Long organizationId);

    List<TicketType> findByOrganizationIsNull();

    Optional<TicketType> findByOrganizationIdAndKey(Long organizationId, String key);
    Optional<TicketType> findByOrganizationIsNullAndKey(String key);
    List<TicketType> findByOrganizationIdAndActiveTrueOrderBySortOrderAscIdAsc(Long organizationId);
    List<TicketType> findByOrganizationIsNullAndActiveTrueOrderBySortOrderAscIdAsc();
}
