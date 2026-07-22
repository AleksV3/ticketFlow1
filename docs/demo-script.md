# TicketFlow1 Service Workflow Demo Script

This script is for a mentor/demo walkthrough of the configurable
service-request workflow slice. The demo profile still provides the base demo
users; T066 will add a shorter seed that preloads a complete runtime-field and
routing scenario.

## Demo accounts

All demo accounts use password `admin123`.

| Persona | Email | Use |
|---|---|---|
| TicketFlow1 admin | `admin@ticketflow1.demo` | Admin configuration, internal TASI/USR work |
| TicketFlow1 agent | `agent@ticketflow1.demo` | Internal workflow execution |
| TicketFlow1 manager | `manager@ticketflow1.demo` | Higher internal approval |
| Alpine contributor | `contributor@alpine.demo` | Client DFCT/REQ creation |
| Alpine approver | `approver@alpine.demo` | Client REQ acceptance |
| Coastal contributor | `contributor@coastal.demo` | Tenant-isolation comparison |

## Opening statement

Say:

> TicketFlow1 is not only a ticket list. It is a workflow engine for service
> requests. Ticket types, subtype forms, routing, permissions, approvals, audit
> history, and tenant isolation are enforced by the backend.

## 1. Show tenant-safe login and dashboard

1. Open `/login`.
2. Log in as `contributor@alpine.demo`.
3. Open the dashboard and ticket list.
4. Explain that the visible tickets are scoped to Alpine Retail.
5. Mention that Coastal Logistics exists but is not visible to this user.

Expected point:

> Organization isolation is a backend rule, not just hidden frontend UI.

## 2. Show client ticket creation limits

1. Open **New Ticket**.
2. Show that the client-facing flow is for client-allowed requests such as
   DFCT and REQ.
3. Create or open a DFCT.
4. Point out severity/SLA fields.

Expected point:

> DFCT uses `DEFECT_SLA` capability, so SLA behavior does not depend on one
> hard-coded ticket key.

## 3. Show admin-configurable service request metadata

1. Log out and log in as `admin@ticketflow1.demo`.
2. Open admin workflow/type configuration.
3. Show TASI, USR, DFCT, and REQ ticket types.
4. Open subtype configuration for TASI or USR.
5. Show subtype ordering and active/deactivated state.
6. Open field configuration for a subtype.
7. Add or inspect a field such as:
   - key: `business_justification`
   - label: `Business justification`
   - kind: `LONG_TEXT`
   - required: `true`
   - visibility: `INTERNAL`

Expected point:

> New subtype fields are stored as configuration and typed values. We do not
> add physical columns to the ticket table for every new business question.

## 4. Show routing configuration

1. Open Teams.
2. Show a team, leader, members, and ticket order.
3. Open routing for a TASI subtype.
4. Select team, primary developer, fallback developer, and approver.

Expected point:

> The selected subtype can determine who receives the work and who must approve
> it.

## 5. Create an internal TASI ticket

1. Open **New Ticket** as the internal admin/agent.
2. Select `TASI`.
3. Select a subtype such as `FIREWALL`.
4. Fill dynamic fields.
5. Submit the ticket.
6. Open the detail page.
7. Point out:
   - ticket key;
   - subtype;
   - dynamic values;
   - assigned team/developer;
   - resolved approver;
   - process map;
   - allowed transitions versus workflow commands.

Expected point:

> Creation is atomic: ticket, dynamic values, routing result, initial status
> history, and audit are saved together.

## 6. Execute TASI/USR protected approval

1. Move the TASI or USR ticket to `ANALYSIS`.
2. Move it to `PENDING_APPROVAL`.
3. Use the protected approve or reject command.
4. If rejecting, enter a reason and show the return path.

Expected point:

> Approval is not a normal status move. It is a protected operation with its
> own permission, relationship, reason, audit, and rollback rules.

## 7. Create a USR/MODIFY ticket

1. Create a `USR` ticket.
2. Select subtype `MODIFY`.
3. Use target-user search.
4. Select a user from the current organization.
5. Submit.
6. Show that the ticket stores the target user and display snapshot.

Expected point:

> User search is bounded and tenant-safe. A client/user from another
> organization is not leaked.

## 8. Show REQ client acceptance

1. Log in as `contributor@alpine.demo` and create/open a REQ.
2. Log in as TicketFlow1 and move it through analysis to client acceptance.
3. Log in as the Alpine approver.
4. Accept or reject the request.

Expected point:

> REQ acceptance belongs to the client business owner or an approved
> same-organization delegate.

## 9. Show collaboration and traceability

1. Add a public comment.
2. Add an internal comment as a TicketFlow1 user.
3. Add an attachment.
4. Show status history.
5. Show audit history.

Expected point:

> Public collaboration is shared with the client, internal notes stay internal,
> and audit/status history prove who changed what and when.

## 10. Closing statement

Say:

> The strongest part of this project is that the important business rules are
> server-side: workflow transitions, protected approvals, tenant isolation,
> dynamic-field validation, routing, audit, and migration safety. The UI helps
> users perform the work, but the backend enforces the rules.
