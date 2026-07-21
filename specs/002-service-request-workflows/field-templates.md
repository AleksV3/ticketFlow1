# Seeded Subtype Field Templates

These templates satisfy T004 and provide a usable default. Administrators may
later edit labels/order/required flags, add fields, or deactivate unused fields
through the bounded configuration model. Stable keys cannot change after use.

All TASI and USR fields are `INTERNAL`; client users cannot retrieve their
definitions or values. Text is plain text, length-bounded, and never executable.

## TASI

Common required fields: `environment` (single select: Development, Test,
Production), `business_justification` (long text, 2,000 characters), and
`requested_completion_date` (date, optional).

| Subtype | Field key | Kind | Required | Notes |
|---|---|---|---|---|
| FIREWALL | `source` | short text | yes | Source host, IP, or CIDR; max 255 |
| FIREWALL | `destination` | short text | yes | Destination host, IP, or CIDR; max 255 |
| FIREWALL | `service_ports` | short text | yes | Protocol and ports; max 500 |
| FIREWALL | `direction` | single select | yes | Inbound, Outbound, Both |
| NETWORK | `site_location` | short text | yes | Site or office; max 255 |
| NETWORK | `network_service` | single select | yes | LAN, WAN, Wi-Fi, VPN, DNS, Other |
| NETWORK | `source_destination` | long text | yes | Endpoints and required connectivity; max 2,000 |
| NETWORK | `bandwidth` | short text | no | Requested capacity; max 100 |
| APPLICATION | `application_name` | short text | yes | Application/service name; max 255 |
| APPLICATION | `change_description` | long text | yes | Requested application work; max 4,000 |
| APPLICATION | `version` | short text | no | Current or target version; max 100 |
| APPLICATION | `affected_users` | integer | no | Minimum 0 |
| HARDWARE | `hardware_kind` | single select | yes | Laptop, Desktop, Server, Peripheral, Other |
| HARDWARE | `asset_tag` | short text | no | Existing asset identifier; max 100 |
| HARDWARE | `location` | short text | yes | Delivery/work location; max 255 |
| HARDWARE | `hardware_requirements` | long text | yes | Specification and purpose; max 4,000 |

## USR

Common optional field: `additional_notes` (long text, 2,000 characters).

| Subtype | Field key | Kind | Required | Notes |
|---|---|---|---|---|
| NEW | `first_name` | short text | yes | Max 100 |
| NEW | `last_name` | short text | yes | Max 100 |
| NEW | `work_email` | short text | yes | Server validates email shape |
| NEW | `department` | short text | yes | Max 255 |
| NEW | `manager` | user reference | yes | Active user in allowed directory scope |
| NEW | `start_date` | date | yes | Requested activation date |
| NEW | `access_profile` | long text | yes | Systems and access required; max 4,000 |
| MODIFY | `target_user` | user reference | yes | Tenant-scoped `app_user`; ID plus snapshot |
| MODIFY | `requested_changes` | long text | yes | Max 4,000 |
| MODIFY | `effective_date` | date | yes | Requested effective date |
| MODIFY | `change_reason` | long text | yes | Max 2,000 |
| DELETE | `target_user` | user reference | yes | Tenant-scoped `app_user`; ID plus snapshot |
| DELETE | `deactivation_date` | date | yes | Requested deactivation date |
| DELETE | `revocation_scope` | multi-select | yes | Account, Roles, Licences, Devices, Other |
| DELETE | `deletion_reason` | long text | yes | Max 2,000 |

