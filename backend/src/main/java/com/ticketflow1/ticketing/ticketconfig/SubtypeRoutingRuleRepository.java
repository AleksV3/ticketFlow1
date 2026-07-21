package com.ticketflow1.ticketing.ticketconfig;
import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository;
public interface SubtypeRoutingRuleRepository extends JpaRepository<SubtypeRoutingRule,Long>{
    Optional<SubtypeRoutingRule> findBySubtypeIdAndOrganizationId(Long subtypeId,Long organizationId);
    Optional<SubtypeRoutingRule> findBySubtypeIdAndOrganizationIsNull(Long subtypeId);
    Optional<SubtypeRoutingRule> findBySubtypeIdAndOrganizationIdAndActiveTrue(Long subtypeId,Long organizationId);
    Optional<SubtypeRoutingRule> findBySubtypeIdAndOrganizationIsNullAndActiveTrue(Long subtypeId);
}
