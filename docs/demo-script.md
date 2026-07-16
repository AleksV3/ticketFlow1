# TicketFlow1 demo script

Target duration: under ten minutes. Start with a clean database and
`SPRING_PROFILES_ACTIVE=demo`, then open `http://localhost:3000/login`.
All demo accounts use the local-only password `admin123`.

## Seeded accounts

| Persona | Email | Expected access |
|---|---|---|
| Client contributor | `contributor@alpine.demo` | Alpine tickets, create/update/public comments |
| Client approver | `approver@alpine.demo` | Alpine tickets and proposal decisions |
| Ticket lead | `agent@ticketflow1.demo` | Cross-org ticket work and internal comments |
| Manager | `manager@ticketflow1.demo` | Cross-org work, including cancellation |
| Administrator | `admin@ticketflow1.demo` | User, role, type, and workflow administration |
| Isolation account | `contributor@coastal.demo` | Coastal tickets only |

## Exact 13-step walkthrough

1. Sign in as `contributor@alpine.demo`. Expected: Dashboard and ticket
   navigation appear; admin navigation does not.
2. Choose **New ticket**, select Alpine Retail and **Change Request**, enter
   title `Enable corporate SSO`, description `Add SAML SSO for Alpine staff`,
   priority `HIGH`, and submit. Expected: a new `TF-*` ticket in `SUBMITTED`.
3. Sign out and sign in as `agent@ticketflow1.demo`. Open the new ticket.
   Expected: the Alpine ticket is visible with internal ticket actions.
4. Under **Available actions**, choose `ANALYSIS`. Expected: status becomes
   `ANALYSIS` and history records Maya Agent.
5. Create a proposal with description `Implement SAML metadata exchange and
   staged rollout`, delivery date two weeks from today, and effort `5 days`.
   Expected: proposal is `PENDING`, ticket is `PROPOSAL`, responsibility is
   with the client.
6. Sign out and sign in as `approver@alpine.demo`; open the ticket. Expected:
   Approve and Reject commands appear, while internal-only comments do not.
7. Enter decision note `Approved for the next release` and choose **Approve**.
   Expected: proposal is `APPROVED`, ticket is `PROPOSAL_APPROVED`, and the
   audit log names Liam Approver.
8. Sign back in as `agent@ticketflow1.demo` and move the ticket through
   `DEVELOPMENT`, `FIRST_OCCURRENCE_TESTING`, `USER_ACCEPTANCE_TESTING`, and
   `READY_FOR_PRODUCTION`. Expected: every transition is appended to history.
9. Add public comment `Ready for Alpine acceptance` and internal comment
   `Deployment checklist verified`. Expected: both appear to the agent; the
   client sees only the public comment. Show the audit and status history.
10. Choose **New ticket**, select Alpine Retail and **Defect**, title
    `Payment gateway unavailable`, description `Checkout authorization times
    out`, priority `CRITICAL`, severity `SEV_1`, and submit. Expected: status
    `REPORTED` with response and information deadlines.
11. Open **Dashboard**. Expected: the seeded `TF-1000` appears in **SLA
    breached**, while the new defect contributes to severity counts.
12. Open the new defect and transition `ANALYSIS` → `FIX_IN_PROGRESS` →
    `CLIENT_CONFIRMATION`. Expected: responsibility changes to `CLIENT`.
13. Return to Dashboard. Expected: active/closed totals, SLA attention lists,
    approval/confirmation queues, severity counts, and assigned work reconcile
    with the tickets shown during the demo.

Isolation proof: sign in as `contributor@coastal.demo`. Expected: `TF-1002` is
visible and all Alpine tickets are absent from lists, dashboard, and direct
ticket URLs.
