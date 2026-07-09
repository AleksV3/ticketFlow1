package com.ticketflow1.ticketing.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRepository extends JpaRepository<Workflow, Long> {

    List<Workflow> findByOrganizationId(Long organizationId);

    List<Workflow> findByOrganizationIsNull();
}
