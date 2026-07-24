package com.ticketflow1.ticketing.workflow;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowStateRepository extends JpaRepository<WorkflowState, Long> {

    Optional<WorkflowState> findByWorkflowIdAndInitialTrue(Long workflowId);

    Optional<WorkflowState> findByWorkflowIdAndKey(Long workflowId, String key);
    Optional<WorkflowState> findByWorkflowIdAndName(Long workflowId, String name);

    List<WorkflowState> findByWorkflowIdOrderBySortOrderAsc(Long workflowId);
}
