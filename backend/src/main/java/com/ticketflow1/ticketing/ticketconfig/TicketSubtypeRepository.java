package com.ticketflow1.ticketing.ticketconfig;
import java.util.List; import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository;
public interface TicketSubtypeRepository extends JpaRepository<TicketSubtype,Long>{
    List<TicketSubtype> findByTicketTypeIdOrderBySortOrderAscIdAsc(Long ticketTypeId);
    Optional<TicketSubtype> findByTicketTypeIdAndKey(Long ticketTypeId,String key);
    boolean existsByTicketTypeId(Long ticketTypeId);
    List<TicketSubtype> findByTicketTypeIdAndActiveTrueOrderBySortOrderAscIdAsc(Long ticketTypeId);
}
