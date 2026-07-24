package com.ticketflow1.ticketing.ticketconfig;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SubtypeFieldRoleGrantRepository extends JpaRepository<SubtypeFieldRoleGrant,Long> {
    List<SubtypeFieldRoleGrant> findByFieldId(Long fieldId);
    List<SubtypeFieldRoleGrant> findByFieldIdIn(Collection<Long> fieldIds);
    boolean existsByFieldId(Long fieldId);
}
