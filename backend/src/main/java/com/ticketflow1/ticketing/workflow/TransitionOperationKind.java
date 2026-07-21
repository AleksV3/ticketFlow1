package com.ticketflow1.ticketing.workflow;

public enum TransitionOperationKind {
    STANDARD,
    PROPOSAL_CREATE,
    PROPOSAL_APPROVE,
    PROPOSAL_REJECT
    ,WORKFLOW_APPROVE, WORKFLOW_REJECT, CORRECTION_RETURN, CLIENT_ACCEPT, CLIENT_REJECT
}
