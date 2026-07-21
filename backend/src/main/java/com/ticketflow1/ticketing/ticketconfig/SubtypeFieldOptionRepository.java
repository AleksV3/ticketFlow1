package com.ticketflow1.ticketing.ticketconfig;
import java.util.List; import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository;
public interface SubtypeFieldOptionRepository extends JpaRepository<SubtypeFieldOption,Long>{
    List<SubtypeFieldOption> findByFieldDefinitionIdOrderBySortOrderAscIdAsc(Long fieldDefinitionId);
    Optional<SubtypeFieldOption> findByFieldDefinitionIdAndKey(Long fieldDefinitionId,String key);
    List<SubtypeFieldOption> findByFieldDefinitionIdAndActiveTrueOrderBySortOrderAscIdAsc(Long fieldDefinitionId);
}
