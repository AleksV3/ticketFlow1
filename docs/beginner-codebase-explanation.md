# TicketFlow1 Codebase Explanation For Beginners

This document explains the TicketFlow1 project as if you are new to coding.
It avoids heavy programming words where possible. When a technical word is
needed, it explains what it means.

## 1. What This Project Is

TicketFlow1 is a web application for managing work tickets.

A ticket is like a tracked request. Someone reports something, the team works
on it, people comment on it, the ticket moves through steps, and finally it is
closed.

This project is not just a simple list of tickets. It is a process tool. That
means tickets follow rules.

For example:

- A Change Request may need a proposal before work starts.
- A client may need to approve that proposal.
- A Defect may need a severity, such as SEV_1 or SEV_2.
- A Defect may have SLA deadlines, meaning response-time promises.
- Some comments are public.
- Some comments are internal and only visible to TicketFlow1 users.
- Some users are allowed to approve things.
- Some users are not.

The main idea is this:

TicketFlow1 stores the work, controls the process, and records what happened.

## 2. The Two Main Parts

The project has two main parts:

```text
backend/
frontend/
```

The backend is the server.

The frontend is the website you see in the browser.

You can think about it like a restaurant:

- The frontend is the waiter and menu. It shows choices to the user.
- The backend is the kitchen and manager. It decides what is allowed and does
  the real work.
- The database is the storage room. It keeps the information permanently.

The frontend should be helpful, but it is not trusted with important rules.
The backend checks the real permissions and workflow rules.

## 3. What The Backend Does

The backend is written in Java with Spring Boot.

Its folder is:

```text
backend/src/main/java/com/ticketflow1/ticketing
```

The backend is responsible for:

- logging users in;
- checking who the user is;
- checking what the user is allowed to do;
- creating tickets;
- updating tickets;
- moving tickets through workflow steps;
- creating and approving proposals;
- saving comments;
- hiding internal comments from clients;
- calculating defect SLA status;
- storing audit history;
- storing status history;
- handling file attachments;
- building dashboard numbers.

The backend is the brain of the application.

## 4. What The Frontend Does

The frontend is written with Next.js, React, and TypeScript.

Its folder is:

```text
frontend/
```

The frontend is responsible for:

- showing the login screen;
- showing the dashboard;
- showing ticket lists;
- showing ticket details;
- showing buttons for allowed transitions;
- showing comments;
- showing attachments;
- showing badges like status, severity, and SLA;
- sending user actions to the backend.

The frontend is what the user clicks and sees.

## 5. What The Database Does

The database stores the real project data.

This project uses PostgreSQL.

The database contains things like:

- users;
- roles;
- permissions;
- organizations;
- tickets;
- workflows;
- workflow states;
- workflow transitions;
- comments;
- attachments;
- proposals;
- audit logs;
- status history.

The database structure is created by migration files here:

```text
backend/src/main/resources/db/migration
```

A migration is a file that changes the database structure or inserts starting
data. For example, one migration creates tables, another adds comment mentions,
another adds security tables.

## 6. The Most Important Idea: Workflow

Workflow means the path a ticket follows.

For example, a Change Request could move like this:

```text
SUBMITTED
ANALYSIS
PROPOSAL
PROPOSAL_APPROVED
DEVELOPMENT
TESTING
PRODUCTION
CLOSED
```

Not every ticket type follows the same path.

A Task might skip proposal approval.

A Defect might include client confirmation and SLA rules.

The important thing is that users cannot move tickets however they want. The
backend checks whether the move is allowed.

The main file for checking workflow moves is:

```text
backend/src/main/java/com/ticketflow1/ticketing/workflow/WorkflowEngine.java
```

This file checks:

- What state is the ticket in now?
- What state does the user want to move it to?
- Does that move exist?
- Does the user have the needed permission?
- Does the user need to be CLIENT or TICKETFLOW1 side?

If the answer is no, the backend rejects the action.

## 7. What A Ticket Contains

The main ticket code is:

```text
backend/src/main/java/com/ticketflow1/ticketing/ticket/Ticket.java
```

This file describes what a ticket is.

A ticket has:

- a ticket key, such as TF-100;
- a ticket type, such as CHANGE_REQUEST, TASK, or DEFECT;
- a current workflow state;
- a title;
- a description;
- a priority;
- maybe a severity;
- an organization;
- the person who reported it;
- the TicketFlow1 person assigned to it;
- who currently has responsibility;
- creation and update dates;
- close date;
- SLA deadline dates for defects.

So when you see a ticket on the screen, the information is coming from this
kind of backend object.

## 8. How Creating A Ticket Works

The main ticket service is:

```text
backend/src/main/java/com/ticketflow1/ticketing/ticket/TicketService.java
```

When someone creates a ticket, the backend roughly does this:

```text
1. Check if the user is allowed to create tickets.
2. Find the current user.
3. Decide which organization the ticket belongs to.
4. Find the ticket type.
5. Find the first workflow state.
6. Check severity rules.
7. Create the ticket.
8. If it is a defect, calculate SLA deadlines.
9. Save the ticket.
10. Write an audit log entry.
11. Write a status history entry.
12. Send the ticket data back to the frontend.
```

This matters because creating a ticket is not only inserting one row. It also
starts the process and records that the process started.

## 9. How Updating A Ticket Works

Updating a ticket means changing fields like:

- title;
- description;
- priority;
- severity;
- ticket lead;
- assigned team.

Status changes are not handled here.

That is important.

Changing a title is a normal edit.

Moving from ANALYSIS to PROPOSAL is a workflow action.

Those two things are separated in the code so the process stays clean.

Normal field edits are handled by:

```text
TicketService.java
```

Status changes are handled by:

```text
TicketTransitionService.java
```

## 10. How Moving A Ticket Works

The main transition code is:

```text
backend/src/main/java/com/ticketflow1/ticketing/ticket/TicketTransitionService.java
```

Transition means moving a ticket from one state to another.

For example:

```text
ANALYSIS -> PROPOSAL
```

or:

```text
DEVELOPMENT -> TESTING
```

When a transition happens, the backend does this:

```text
1. Load the ticket.
2. Ask WorkflowEngine if the move is allowed.
3. Check if the move requires a comment or reason.
4. Change the ticket state.
5. Change who has responsibility, CLIENT or TICKETFLOW1.
6. If the state is final, set the closed date.
7. If this move triggers billing, set the UAT confirmed date.
8. If this is a defect, update the next SLA update time.
9. Save the ticket.
10. Write status history.
11. Write audit log.
12. Add a transition comment if one was given.
13. Send the updated ticket back to the frontend.
```

All of this happens together. If one step fails, the whole action is cancelled.

That protects the data from becoming half-updated.

## 11. What Proposal Approval Means

Change Requests can require proposals.

A proposal is like this:

"Here is what we plan to do, how much effort it takes, when we expect to
deliver it, and maybe how much it costs."

Proposal code is here:

```text
backend/src/main/java/com/ticketflow1/ticketing/proposal/ChangeProposalService.java
```

Creating a proposal does not only save a proposal.

It also moves the ticket into the proposal step.

Approving a proposal does not only update the proposal.

It also moves the ticket forward.

Rejecting a proposal also moves the ticket into a rejected proposal state.

This keeps the proposal and the ticket workflow connected.

## 12. What Comments Do

Comment code is here:

```text
backend/src/main/java/com/ticketflow1/ticketing/comment/CommentService.java
```

There are two main comment types:

```text
PUBLIC
INTERNAL
```

Public comments can be seen by clients.

Internal comments are only for TicketFlow1-side users.

The backend does not simply hide internal comments in the browser. It prevents
client users from receiving them from the API.

That is much safer.

Comments can also mention users, be edited, be soft-deleted, and create live
events so the frontend can update.

Soft-delete means the comment is marked deleted instead of being fully removed.
That helps preserve history.

## 13. What Attachments Do

Attachment code is here:

```text
backend/src/main/java/com/ticketflow1/ticketing/attachment/AttachmentService.java
```

Attachments are uploaded files.

The backend checks:

- Is the user allowed to update the ticket?
- Is the file empty?
- Is the file too large?
- Is the file type allowed?
- Does the file content match the file extension?

The project stores file information in the database and file bytes in object
storage, such as MinIO or S3.

That is better than stuffing big files directly into ticket rows.

## 14. What SLA Means

SLA means Service Level Agreement.

In simple words:

"How fast do we promise to respond or update the client?"

SLA code is here:

```text
backend/src/main/java/com/ticketflow1/ticketing/sla/SlaCalculator.java
backend/src/main/java/com/ticketflow1/ticketing/sla/SlaStatusService.java
```

For defects, the severity controls deadlines.

For example:

- SEV_1 is urgent.
- SEV_2 is serious.
- SEV_3 is lower.
- SEV_4 is lowest.

The calculator creates deadline times.

The status service checks whether the ticket is:

```text
OK
DUE_SOON
BREACHED
NOT_APPLICABLE
```

The project does not store SLA status as a permanent value. It calculates it
when reading the ticket.

That means the app can say "this is now breached" based on the current time,
without needing a background job to update every ticket.

## 15. What Audit Log Means

An audit log is the official record of what happened.

It answers questions like:

- Who created this ticket?
- Who changed the priority?
- Who moved it to testing?
- Who approved the proposal?
- Who added a comment?
- Who deleted an attachment?

Audit code is in:

```text
backend/src/main/java/com/ticketflow1/ticketing/audit
```

Audit logs should not be casually edited or deleted because they are the
history of the system.

## 16. What Status History Means

Status history is more specific than audit.

Audit says many kinds of things happened.

Status history focuses only on ticket movement.

Example:

```text
SUBMITTED -> ANALYSIS
ANALYSIS -> PROPOSAL
PROPOSAL -> PROPOSAL_APPROVED
```

Status history code is in:

```text
backend/src/main/java/com/ticketflow1/ticketing/statushistory
```

This is useful for showing the ticket timeline.

## 17. What The Dashboard Does

Dashboard code is here:

```text
backend/src/main/java/com/ticketflow1/ticketing/dashboard/DashboardService.java
```

The dashboard shows summary information:

- active ticket count;
- closed ticket count;
- tickets by type;
- tickets by status;
- defects by severity;
- breached SLA tickets;
- due soon SLA tickets;
- tickets waiting for client approval;
- tickets waiting for client confirmation;
- tickets assigned to the current user.

The frontend page is:

```text
frontend/app/(app)/dashboard/page.tsx
```

The dashboard frontend asks the backend for numbers and lists, then displays
them.

## 18. Frontend Pages

The main frontend pages are in:

```text
frontend/app
```

Important pages:

```text
frontend/app/login/page.tsx
frontend/app/(app)/dashboard/page.tsx
frontend/app/(app)/tickets/page.tsx
frontend/app/(app)/tickets/[ticketKey]/page.tsx
frontend/app/(app)/tickets/new/page.tsx
```

The `login` page is separate.

The pages inside `(app)` share the logged-in layout with the sidebar.

The folder name `(app)` is a Next.js route group. The parentheses are not part
of the URL. They are just for organizing pages.

## 19. Frontend Layout

The logged-in app layout is:

```text
frontend/app/(app)/layout.tsx
```

It does this:

```text
1. Sets up React Query.
2. Checks the current user.
3. Redirects to login if needed.
4. Shows the sidebar.
5. Starts the live event provider.
6. Shows the page content.
```

React Query is a frontend library that helps fetch data, cache it, and refresh
it after changes.

## 20. How The Frontend Calls The Backend

The main API helper is:

```text
frontend/lib/api.ts
```

Instead of every page writing its own fetch code, the app uses this helper.

It does:

- normal GET requests;
- POST requests;
- PATCH requests;
- DELETE requests;
- uploads;
- downloads;
- CSRF protection for changes;
- login redirect after expired session;
- timeout handling;
- error formatting.

This keeps API behavior consistent across the frontend.

## 21. React Query Hooks

The main query file is:

```text
frontend/lib/queries.ts
```

It has reusable functions like:

```text
useCurrentUser()
useTickets()
useTicket()
useTicketComments()
useTicketAttachments()
useTicketActivity()
```

These are used by pages and components to load data.

For example, the ticket detail page uses `useTicket(ticketKey)` to load one
ticket.

## 22. Ticket List And Ticket Detail

The ticket list component is:

```text
frontend/components/TicketList.tsx
```

It shows a list of tickets with:

- ticket key;
- title;
- status badge;
- SLA badge;
- organization;
- ticket lead.

The ticket detail page is:

```text
frontend/app/(app)/tickets/[ticketKey]/page.tsx
```

It shows:

- ticket title;
- status;
- severity;
- priority;
- responsibility;
- next actions;
- transition buttons;
- details;
- SLA;
- proposal;
- activity;
- comments;
- attachments.

The detail page mostly displays what the backend sends.

For example, the backend sends `allowedTransitions`, and the frontend turns
those into buttons.

That is good because the frontend does not need to know all workflow rules.

## 23. Live Comment Updates

Live events are handled in:

```text
frontend/lib/events.tsx
backend/src/main/java/com/ticketflow1/ticketing/event
```

This lets the browser know when comments are created, edited, deleted, or when
mentions happen.

It uses Server-Sent Events.

That means the browser opens a long connection to the backend and the backend
can send small updates.

The event does not contain the full private comment body. It only tells the
frontend that something changed. Then the frontend refetches the correct data
through normal secure API calls.

## 24. Full Example: User Logs In And Opens A Ticket

Here is the simple flow:

```text
1. User opens the login page.
2. User enters email and password.
3. Frontend sends login request to backend.
4. Backend checks the password.
5. Backend creates a secure session cookie.
6. Frontend redirects to dashboard.
7. App layout asks backend: who is the current user?
8. Backend answers with user details and permissions.
9. User opens tickets page.
10. Frontend asks backend for ticket list.
11. Backend returns only tickets the user is allowed to see.
12. User clicks a ticket.
13. Frontend asks backend for ticket detail.
14. Backend returns ticket data, allowed transitions, proposal, and SLA info.
15. Frontend renders the ticket detail page.
```

## 25. Full Example: User Moves A Ticket

Here is what happens when a user clicks a transition button:

```text
1. Backend sends ticket detail with allowedTransitions.
2. Frontend shows those transitions as buttons.
3. User clicks one button.
4. Frontend sends transition request to backend.
5. Backend loads the ticket.
6. Backend checks WorkflowEngine.
7. WorkflowEngine checks if this move exists.
8. WorkflowEngine checks if the user has permission.
9. WorkflowEngine checks if the user is on the right side, CLIENT or TICKETFLOW1.
10. If allowed, backend updates the ticket state.
11. Backend updates responsibility.
12. Backend writes status history.
13. Backend writes audit log.
14. Backend may add a comment.
15. Backend returns updated ticket data.
16. Frontend refreshes the screen.
```

If the user is not allowed, the backend rejects the request.

This matters because even if someone manually tries to call the API, the
backend still protects the workflow.

## 26. Full Example: Client Approves A Proposal

```text
1. TicketFlow1 user creates a proposal during ANALYSIS.
2. Backend moves the ticket to PROPOSAL.
3. Client approver opens the ticket.
4. Client clicks approve.
5. Frontend sends approval request.
6. Backend checks proposal exists and is still pending.
7. Backend checks organization access.
8. Backend checks workflow permission for approval.
9. Backend moves ticket to PROPOSAL_APPROVED.
10. Backend marks proposal APPROVED.
11. Backend writes audit log.
12. Frontend refreshes ticket detail.
```

This keeps the proposal and workflow connected.

## 27. Full Example: Defect SLA

```text
1. User creates a DEFECT ticket.
2. User chooses severity, such as SEV_1.
3. Backend calculates response and update deadlines.
4. Ticket is saved with those deadline times.
5. Later, when ticket is viewed, backend compares deadlines to current time.
6. Backend says SLA is OK, DUE_SOON, or BREACHED.
7. Frontend shows the SLA badge.
```

The SLA status changes naturally as time passes because it is calculated when
the ticket is read.

## 28. Why The Code Is Split Into Many Files

At first, many files can feel confusing.

But each file has a job.

For example:

```text
TicketController.java
```

Handles web requests about tickets.

```text
TicketService.java
```

Handles normal ticket business rules.

```text
TicketTransitionService.java
```

Handles ticket state changes.

```text
WorkflowEngine.java
```

Checks if a transition is allowed.

```text
Ticket.java
```

Describes what a ticket is.

```text
TicketDetailMapper.java
```

Builds the ticket response sent to the frontend.

This is better than putting everything in one huge file.

## 29. What Files To Read First

If you are learning this codebase, read these first:

```text
backend/src/main/java/com/ticketflow1/ticketing/ticket/Ticket.java
backend/src/main/java/com/ticketflow1/ticketing/ticket/TicketController.java
backend/src/main/java/com/ticketflow1/ticketing/ticket/TicketService.java
backend/src/main/java/com/ticketflow1/ticketing/workflow/WorkflowEngine.java
backend/src/main/java/com/ticketflow1/ticketing/ticket/TicketTransitionService.java
backend/src/main/java/com/ticketflow1/ticketing/proposal/ChangeProposalService.java
backend/src/main/java/com/ticketflow1/ticketing/comment/CommentService.java
frontend/lib/api.ts
frontend/lib/queries.ts
frontend/app/(app)/tickets/[ticketKey]/page.tsx
```

Read them slowly. Do not try to understand everything at once.

Start with this question:

"What is this file responsible for?"

Then ask:

"What does it call next?"

That is how you follow the flow.

## 30. The Short Version

TicketFlow1 works like this:

```text
User clicks something in the frontend.
Frontend sends a request to the backend.
Backend checks login, permissions, organization, and workflow.
Backend changes data if allowed.
Backend writes audit/history.
Backend sends updated data back.
Frontend redraws the screen.
```

The most important backend idea is:

```text
Do not trust the frontend with business rules.
```

The most important project idea is:

```text
Tickets are not just records. They are controlled workflows.
```

