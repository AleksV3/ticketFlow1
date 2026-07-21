package com.ticketflow1.ticketing.ticketconfig;
import java.util.List; import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository;
public interface SubtypeFieldDefinitionRepository extends JpaRepository<SubtypeFieldDefinition,Long>{
    List<SubtypeFieldDefinition> findBySubtypeIdOrderBySortOrderAscIdAsc(Long subtypeId);
    Optional<SubtypeFieldDefinition> findBySubtypeIdAndKey(Long subtypeId,String key);
    List<SubtypeFieldDefinition> findBySubtypeIdAndActiveTrueOrderBySortOrderAscIdAsc(Long subtypeId);
}
