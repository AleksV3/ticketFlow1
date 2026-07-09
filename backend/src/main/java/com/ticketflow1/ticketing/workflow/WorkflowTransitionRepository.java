package com.ticketflow1.ticketing.workflow;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowTransitionRepository extends JpaRepository<WorkflowTransition, Long> {

    List<WorkflowTransition> findByWorkflowIdAndFromStateId(Long workflowId, Long fromStateId);

    Optional<WorkflowTransition> findByWorkflowIdAndFromStateIdAndToStateId(
            Long workflowId, Long fromStateId, Long toStateId);
}
