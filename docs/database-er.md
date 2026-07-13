# TicketFlow1 Live Database ER

This diagram reflects the current PostgreSQL schema after Flyway migrations V1-V3 have been applied.

```mermaid
erDiagram
    ORGANIZATION ||--o{ APP_USER : has_members
    ORGANIZATION ||--o{ ROLE : owns_client_roles
    ORGANIZATION ||--o{ TICKET_TYPE : owns_client_types
    ORGANIZATION ||--o{ WORKFLOW : owns_client_workflows
    ORGANIZATION ||--o{ TICKET : owns

    ROLE ||--o{ APP_USER : assigned_to
    ROLE ||--o{ ROLE_PERMISSION : grants
    PERMISSION ||--o{ ROLE_PERMISSION : granted_by

    WORKFLOW ||--o{ WORKFLOW_STATE : has
    WORKFLOW ||--o{ WORKFLOW_TRANSITION : has
    WORKFLOW_STATE ||--o{ WORKFLOW_TRANSITION : from_state
    WORKFLOW_STATE ||--o{ WORKFLOW_TRANSITION : to_state
    PERMISSION ||--o{ WORKFLOW_TRANSITION : gates

    WORKFLOW ||--o{ TICKET_TYPE : defines
    TICKET_TYPE ||--o{ TICKET : categorizes
    WORKFLOW_STATE ||--o{ TICKET : current_state_of
    APP_USER ||--o{ TICKET : business_owner_or_lead
    APP_USER ||--o{ AUDIT_LOG : acts_as
    APP_USER ||--o{ STATUS_HISTORY : changes
    TICKET ||--o{ AUDIT_LOG : has
    TICKET ||--o{ STATUS_HISTORY : has

    PERMISSION {
        bigint id PK
        varchar key UK
    }

    ROLE {
        bigint id PK
        varchar name
        varchar party
        bigint organization_id FK
        boolean is_template
        timestamptz updated_at
        bigint updated_by_id FK
    }

    ROLE_PERMISSION {
        bigint role_id PK, FK
        bigint permission_id PK, FK
    }

    ORGANIZATION {
        bigint id PK
        varchar name UK
        boolean active
        timestamptz created_at
        timestamptz updated_at
        bigint updated_by_id FK
    }

    APP_USER {
        bigint id PK
        varchar email UK
        varchar password_hash
        varchar display_name
        varchar party
        bigint role_id FK
        bigint organization_id FK
        boolean active
        timestamptz created_at
        timestamptz updated_at
        bigint updated_by_id FK
    }

    WORKFLOW {
        bigint id PK
        varchar name
        bigint organization_id FK
        timestamptz updated_at
        bigint updated_by_id FK
    }

    WORKFLOW_STATE {
        bigint id PK
        bigint workflow_id FK
        varchar key
        boolean is_initial
        boolean is_terminal
        int sort_order
        timestamptz updated_at
        bigint updated_by_id FK
    }

    WORKFLOW_TRANSITION {
        bigint id PK
        bigint workflow_id FK
        bigint from_state_id FK
        bigint to_state_id FK
        bigint required_permission_id FK
        varchar required_party
        varchar responsibility_after
        timestamptz updated_at
        bigint updated_by_id FK
    }

    TICKET_TYPE {
        bigint id PK
        varchar key
        varchar name
        bigint workflow_id FK
        bigint organization_id FK
        boolean is_template
        boolean requires_proposal
        timestamptz updated_at
        bigint updated_by_id FK
    }

    TICKET {
        bigint id PK
        varchar ticket_key UK
        bigint ticket_type_id FK
        bigint current_state_id FK
        varchar priority
        varchar severity
        varchar title
        text description
        bigint organization_id FK
        bigint business_owner_id FK
        bigint ticket_lead_id FK
        varchar assigned_team
        varchar current_responsibility
        timestamptz created_at
        timestamptz updated_at
        bigint updated_by_id FK
        timestamptz closed_at
        timestamptz response_due_at
        timestamptz first_info_due_at
        timestamptz next_update_due_at
    }

    AUDIT_LOG {
        bigint id PK
        bigint ticket_id FK
        bigint actor_id FK
        varchar action
        varchar field_name
        text old_value
        text new_value
        timestamptz created_at
    }

    STATUS_HISTORY {
        bigint id PK
        bigint ticket_id FK
        bigint from_state_id FK
        bigint to_state_id FK
        bigint changed_by_id FK
        timestamptz created_at
    }
```

## Connection details

Use these values in DBeaver:

- Host: `localhost`
- Port: `5433`
- Database: `ticketflow1_ticketing`
- User: `ticketflow1`
- Password: `ticketflow1`
