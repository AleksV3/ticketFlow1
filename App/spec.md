# AI Coding Agent Specification: Ticketing Application

## Project Name

Ticketing Application

## Goal

Build a web-based ticketing application that allows users to create, assign, track, update, and close tickets. The system should support three ticket types:

1. Change Requests
2. Tasks
3. Defects

The application should be simple, clean, and practical. Focus on core ticket lifecycle functionality first.

---

# 1. Recommended Tech Stack

Use the following stack unless instructed otherwise:

## Frontend

* React
* TypeScript
* Tailwind CSS
* React Router
* Axios or Fetch API

## Backend

* Node.js
* Express.js
* TypeScript

## Database

* PostgreSQL

## Authentication

* JWT authentication
* Password hashing with bcrypt

## File Uploads

* Multer for local file uploads

## Optional ORM

Use Prisma if possible.

---

# 2. Main Application Features

The application must include:

* User registration and login
* Role-based access control
* Ticket creation
* Ticket editing
* Ticket assignment
* Ticket status tracking
* Ticket comments
* File attachments
* Ticket activity history
* Dashboard
* Search and filtering
* Basic notifications
* Basic reports

---

# 3. User Roles

The system should support four roles.

## Admin

Admins can:

* View all tickets
* Create tickets
* Edit all tickets
* Assign tickets
* Change ticket status
* Approve or reject change requests
* Manage users
* View reports
* Manage categories, statuses, and priorities if implemented

## Manager

Managers can:

* View all tickets
* Create tickets
* Edit tickets
* Assign tickets
* Change ticket status
* Approve or reject change requests
* View reports

## Agent

Agents can:

* View tickets assigned to them
* View tickets they created
* Update assigned tickets
* Change status of assigned tickets
* Add comments
* Upload attachments

## Requester

Requesters can:

* Create tickets
* View their own tickets
* Add comments to their own tickets
* Upload attachments to their own tickets
* Reopen their own closed tickets if allowed

---

# 4. Ticket Types

The system must support these ticket types:

```ts
CHANGE_REQUEST
TASK
DEFECT
```

## Change Request

Used for requesting new features, improvements, or changes.

## Task

Used for normal work items.

## Defect

Used for bugs, errors, or issues that need fixing.

---

# 5. Ticket Priority Levels

The system must support these priority levels:

```ts
LOW
MEDIUM
HIGH
CRITICAL
```

---

# 6. Ticket Statuses

Use these statuses:

```ts
NEW
SUBMITTED
UNDER_REVIEW
APPROVED
REJECTED
ASSIGNED
IN_PROGRESS
WAITING_FOR_INFORMATION
FIXED
TESTING
RESOLVED
COMPLETED
CLOSED
REOPENED
CANCELLED
```

Not every ticket type needs to use every status.

---

# 7. Ticket Workflows

## 7.1 Change Request Workflow

Change requests should follow this flow:

```text
SUBMITTED
→ UNDER_REVIEW
→ APPROVED or REJECTED
→ IN_PROGRESS
→ COMPLETED
→ CLOSED
```

Rules:

* A requester can create a change request.
* A manager or admin must approve or reject it.
* If approved, it can be assigned and worked on.
* If rejected, it should not move to in progress.
* Completed change requests can be closed.

## 7.2 Task Workflow

Tasks should follow this flow:

```text
NEW
→ ASSIGNED
→ IN_PROGRESS
→ COMPLETED
→ CLOSED
```

Rules:

* Tasks can be created by admins, managers, agents, or requesters.
* Managers and admins can assign tasks.
* Agents can update tasks assigned to them.

## 7.3 Defect Workflow

Defects should follow this flow:

```text
NEW
→ ASSIGNED
→ IN_PROGRESS
→ FIXED
→ TESTING
→ RESOLVED
→ CLOSED
```

Optional reopen flow:

```text
CLOSED
→ REOPENED
→ IN_PROGRESS
```

Rules:

* Defects are used for bugs and issues.
* Defects should include steps to reproduce if possible.
* A resolved defect can be reopened if the issue still exists.

---

# 8. Database Models

Create the following database models.

---

## 8.1 User Model

```ts
User {
  id: string
  name: string
  email: string
  passwordHash: string
  role: UserRole
  createdAt: Date
  updatedAt: Date
}
```

User role enum:

```ts
enum UserRole {
  ADMIN
  MANAGER
  AGENT
  REQUESTER
}
```

---

## 8.2 Ticket Model

```ts
Ticket {
  id: string
  ticketNumber: string
  title: string
  description: string
  type: TicketType
  status: TicketStatus
  priority: TicketPriority
  createdById: string
  assignedToId?: string
  dueDate?: Date
  closedAt?: Date
  createdAt: Date
  updatedAt: Date
}
```

Ticket type enum:

```ts
enum TicketType {
  CHANGE_REQUEST
  TASK
  DEFECT
}
```

Ticket priority enum:

```ts
enum TicketPriority {
  LOW
  MEDIUM
  HIGH
  CRITICAL
}
```

Ticket status enum:

```ts
enum TicketStatus {
  NEW
  SUBMITTED
  UNDER_REVIEW
  APPROVED
  REJECTED
  ASSIGNED
  IN_PROGRESS
  WAITING_FOR_INFORMATION
  FIXED
  TESTING
  RESOLVED
  COMPLETED
  CLOSED
  REOPENED
  CANCELLED
}
```

---

## 8.3 Comment Model

```ts
Comment {
  id: string
  ticketId: string
  userId: string
  body: string
  createdAt: Date
  updatedAt: Date
}
```

---

## 8.4 Attachment Model

```ts
Attachment {
  id: string
  ticketId: string
  uploadedById: string
  fileName: string
  filePath: string
  fileType: string
  fileSize: number
  createdAt: Date
}
```

---

## 8.5 Activity History Model

```ts
Activity {
  id: string
  ticketId: string
  userId: string
  action: string
  oldValue?: string
  newValue?: string
  createdAt: Date
}
```

Examples of activity actions:

```ts
TICKET_CREATED
TICKET_UPDATED
STATUS_CHANGED
ASSIGNEE_CHANGED
PRIORITY_CHANGED
COMMENT_ADDED
ATTACHMENT_UPLOADED
TICKET_CLOSED
TICKET_REOPENED
CHANGE_APPROVED
CHANGE_REJECTED
```

---

## 8.6 Notification Model

```ts
Notification {
  id: string
  userId: string
  ticketId?: string
  message: string
  isRead: boolean
  createdAt: Date
}
```

---

# 9. Backend API Requirements

Create REST API routes.

---

## 9.1 Authentication Routes

```http
POST /api/auth/register
POST /api/auth/login
GET /api/auth/me
POST /api/auth/logout
```

## Register Request

```json
{
  "name": "User Name",
  "email": "user@example.com",
  "password": "password123"
}
```

## Login Request

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

## Auth Response

```json
{
  "token": "jwt-token",
  "user": {
    "id": "user-id",
    "name": "User Name",
    "email": "user@example.com",
    "role": "REQUESTER"
  }
}
```

---

## 9.2 User Routes

```http
GET /api/users
GET /api/users/:id
PATCH /api/users/:id
DELETE /api/users/:id
```

Rules:

* Only admins can delete users.
* Admins and managers can view user lists.
* Users can view their own profile.

---

## 9.3 Ticket Routes

```http
GET /api/tickets
POST /api/tickets
GET /api/tickets/:id
PATCH /api/tickets/:id
DELETE /api/tickets/:id
PATCH /api/tickets/:id/assign
PATCH /api/tickets/:id/status
PATCH /api/tickets/:id/close
PATCH /api/tickets/:id/reopen
```

## Create Ticket Request

```json
{
  "title": "Login page error",
  "description": "The login page shows an error when I submit the form.",
  "type": "DEFECT",
  "priority": "HIGH",
  "assignedToId": null,
  "dueDate": null
}
```

## Ticket Response

```json
{
  "id": "ticket-id",
  "ticketNumber": "TCK-0001",
  "title": "Login page error",
  "description": "The login page shows an error when I submit the form.",
  "type": "DEFECT",
  "status": "NEW",
  "priority": "HIGH",
  "createdBy": {
    "id": "user-id",
    "name": "User Name"
  },
  "assignedTo": null,
  "createdAt": "2026-07-06T10:00:00Z",
  "updatedAt": "2026-07-06T10:00:00Z"
}
```

---

## 9.4 Comment Routes

```http
GET /api/tickets/:ticketId/comments
POST /api/tickets/:ticketId/comments
PATCH /api/comments/:id
DELETE /api/comments/:id
```

## Create Comment Request

```json
{
  "body": "I checked this issue and can reproduce it."
}
```

Rules:

* Users who can view the ticket can view comments.
* Users who can access the ticket can add comments.
* Admins can delete comments.
* Comment author can edit their own comment.

---

## 9.5 Attachment Routes

```http
GET /api/tickets/:ticketId/attachments
POST /api/tickets/:ticketId/attachments
GET /api/attachments/:id/download
DELETE /api/attachments/:id
```

Rules:

* Users who can view the ticket can view attachments.
* Users who can access the ticket can upload attachments.
* Admins and uploaders can delete attachments.
* Limit upload size to 10MB.
* Allow common file types such as PDF, PNG, JPG, DOCX, XLSX, TXT.

---

## 9.6 Activity Routes

```http
GET /api/tickets/:ticketId/activity
```

Rules:

* Activity history should be read-only.
* Activity should be automatically created when important ticket changes happen.

---

## 9.7 Notification Routes

```http
GET /api/notifications
PATCH /api/notifications/:id/read
PATCH /api/notifications/read-all
```

Rules:

* Users can only view their own notifications.
* Notifications should be created when tickets are assigned, updated, commented on, closed, reopened, approved, or rejected.

---

## 9.8 Dashboard Routes

```http
GET /api/dashboard/summary
GET /api/dashboard/my-tickets
GET /api/dashboard/recent
```

Dashboard summary response should include:

```json
{
  "totalTickets": 50,
  "openTickets": 22,
  "closedTickets": 18,
  "assignedToMe": 7,
  "byStatus": {
    "NEW": 5,
    "IN_PROGRESS": 10,
    "CLOSED": 18
  },
  "byType": {
    "CHANGE_REQUEST": 12,
    "TASK": 20,
    "DEFECT": 18
  },
  "byPriority": {
    "LOW": 8,
    "MEDIUM": 20,
    "HIGH": 15,
    "CRITICAL": 7
  }
}
```

---

## 9.9 Report Routes

```http
GET /api/reports/tickets
GET /api/reports/resolution-times
GET /api/reports/by-user
```

Basic reports should show:

* Total tickets
* Open tickets
* Closed tickets
* Tickets by type
* Tickets by status
* Tickets by priority
* Average resolution time
* Tickets assigned to each user

---

# 10. Frontend Pages

Create the following pages.

---

## 10.1 Login Page

Route:

```text
/login
```

Features:

* Email input
* Password input
* Login button
* Error display
* Link to register page if registration is enabled

---

## 10.2 Register Page

Route:

```text
/register
```

Features:

* Name input
* Email input
* Password input
* Confirm password input
* Register button

Default new user role should be:

```ts
REQUESTER
```

---

## 10.3 Dashboard Page

Route:

```text
/dashboard
```

Show:

* Total tickets
* Open tickets
* Closed tickets
* Tickets assigned to current user
* Recently updated tickets
* Tickets grouped by status
* Tickets grouped by priority

---

## 10.4 Ticket List Page

Route:

```text
/tickets
```

Show ticket table with:

* Ticket number
* Title
* Type
* Status
* Priority
* Assigned user
* Created date
* Updated date

Features:

* Search by title or ticket number
* Filter by status
* Filter by type
* Filter by priority
* Filter by assigned user
* Sort by created date
* Sort by updated date
* Button to create ticket

---

## 10.5 Ticket Details Page

Route:

```text
/tickets/:id
```

Show:

* Ticket number
* Title
* Description
* Type
* Status
* Priority
* Created by
* Assigned to
* Created date
* Updated date
* Due date
* Comments
* Attachments
* Activity history

Actions:

* Edit ticket
* Assign ticket
* Change status
* Add comment
* Upload attachment
* Close ticket
* Reopen ticket

Only show actions the current user has permission to perform.

---

## 10.6 Create Ticket Page

Route:

```text
/tickets/new
```

Fields:

* Title
* Description
* Type
* Priority
* Assigned user, optional
* Due date, optional
* Attachments, optional

Default status logic:

* If type is `CHANGE_REQUEST`, default status should be `SUBMITTED`.
* If type is `TASK`, default status should be `NEW`.
* If type is `DEFECT`, default status should be `NEW`.

---

## 10.7 Edit Ticket Page

Route:

```text
/tickets/:id/edit
```

Fields:

* Title
* Description
* Type
* Priority
* Assigned user
* Due date

Do not allow unauthorized users to edit restricted fields.

---

## 10.8 Reports Page

Route:

```text
/reports
```

Show:

* Ticket totals
* Tickets by status
* Tickets by type
* Tickets by priority
* Average resolution time
* Tickets by assigned user

Charts are optional.

---

## 10.9 Admin Users Page

Route:

```text
/admin/users
```

Show:

* User list
* Name
* Email
* Role
* Created date

Admin actions:

* Change user role
* Delete user
* View user details

Only admins can access this page.

---

# 11. UI Layout

The application should have:

* Sidebar navigation
* Top header
* Main content area
* Clean dashboard cards
* Tables for ticket lists
* Forms for creating and editing tickets
* Status badges
* Priority badges
* Toast messages for success and error feedback

Main navigation links:

```text
Dashboard
Tickets
Create Ticket
Reports
Admin
Profile
Logout
```

Only show Admin link to admins.

---

# 12. Permission Rules

Implement role-based permissions.

## Admin

Can do everything.

## Manager

Can:

* View all tickets
* Assign tickets
* Edit tickets
* Change statuses
* Approve change requests
* View reports

Cannot:

* Delete users
* Change system-level settings unless allowed

## Agent

Can:

* View tickets assigned to them
* View tickets created by them
* Update assigned tickets
* Comment on accessible tickets
* Upload attachments to accessible tickets

Cannot:

* Approve change requests
* Manage users
* View all reports unless allowed

## Requester

Can:

* Create tickets
* View own tickets
* Comment on own tickets
* Upload attachments to own tickets
* Reopen own closed tickets if allowed

Cannot:

* Assign tickets
* Approve changes
* View other users’ tickets
* Manage users

---

# 13. Search and Filter Requirements

The ticket list endpoint should support query parameters:

```http
GET /api/tickets?search=&status=&type=&priority=&assignedToId=&createdById=&sortBy=updatedAt&sortOrder=desc
```

Supported filters:

* Search text
* Status
* Ticket type
* Priority
* Assigned user
* Created by user
* Created date range
* Updated date range

Supported sorting:

* Created date
* Updated date
* Priority
* Status

---

# 14. Notification Rules

Create notifications for these events:

1. Ticket assigned to user
2. Ticket status changed
3. Comment added
4. Attachment uploaded
5. Change request approved
6. Change request rejected
7. Ticket closed
8. Ticket reopened

Notification message examples:

```text
You have been assigned ticket TCK-0004.
Ticket TCK-0004 status changed to In Progress.
A new comment was added to ticket TCK-0004.
Change request TCK-0004 was approved.
```

---

# 15. Activity History Rules

Create an activity record whenever:

* A ticket is created
* A ticket title changes
* A ticket description changes
* A ticket status changes
* A ticket priority changes
* A ticket is assigned
* A comment is added
* An attachment is uploaded
* A change request is approved
* A change request is rejected
* A ticket is closed
* A ticket is reopened

Activity history should display:

* User who performed the action
* Action type
* Old value if relevant
* New value if relevant
* Date and time

---

# 16. Validation Rules

## User Validation

* Name is required.
* Email is required.
* Email must be unique.
* Password is required.
* Password must be at least 8 characters.

## Ticket Validation

* Title is required.
* Title must be at least 3 characters.
* Description is required.
* Type is required.
* Priority is required.
* Status must be valid.
* Assigned user must exist if provided.
* Due date must be a valid date if provided.

## Comment Validation

* Comment body is required.
* Comment body cannot be empty.

## Attachment Validation

* File is required.
* Max file size should be 10MB.
* Allowed file types:

  * PDF
  * PNG
  * JPG
  * JPEG
  * DOCX
  * XLSX
  * TXT

---

# 17. Error Handling

Use consistent API error responses.

Example:

```json
{
  "message": "Ticket not found",
  "statusCode": 404
}
```

Common errors:

* 400 Bad Request
* 401 Unauthorized
* 403 Forbidden
* 404 Not Found
* 500 Server Error

Frontend should show user-friendly error messages.

---

# 18. Security Requirements

Implement:

* Password hashing
* JWT authentication
* Protected routes
* Role-based authorization
* Server-side validation
* File upload validation
* Safe error messages
* CORS configuration
* Environment variables for secrets

Do not store plain text passwords.

---

# 19. Minimum Viable Product

Build the MVP first.

The MVP must include:

1. Register
2. Login
3. Logout
4. Role-based access
5. Create ticket
6. View ticket list
7. View ticket details
8. Edit ticket
9. Assign ticket
10. Change ticket status
11. Add comments
12. Upload attachments
13. Activity history
14. Dashboard summary
15. Search and filters

Do not build optional advanced features until MVP is complete.

---

# 20. Future Features

After the MVP works, add these if there is time:

* Kanban board
* Calendar view
* Timeline view
* User mentions in comments
* Internal and public comments
* Email notifications
* Due dates and reminders
* Full audit log
* Time tracking
* Charts and graphs
* PDF export
* Excel export
* Saved filters
* Tags and labels
* Duplicate ticket
* Custom workflows
* Custom statuses
* Admin settings page

---

# 21. Suggested Folder Structure

Use a clean structure.

## Backend

```text
backend/
  src/
    config/
    controllers/
    middleware/
    routes/
    services/
    models/
    prisma/
    utils/
    validators/
    uploads/
    app.ts
    server.ts
  package.json
  .env
```

## Frontend

```text
frontend/
  src/
    api/
    components/
    context/
    hooks/
    layouts/
    pages/
    routes/
    types/
    utils/
    App.tsx
    main.tsx
  package.json
```

---

# 22. Backend Implementation Steps

Follow this order:

1. Set up Express server.
2. Set up PostgreSQL connection.
3. Set up Prisma schema.
4. Create User model.
5. Create authentication routes.
6. Add JWT middleware.
7. Add role authorization middleware.
8. Create Ticket model.
9. Create ticket CRUD routes.
10. Add ticket assignment route.
11. Add ticket status update route.
12. Add Comment model and routes.
13. Add Attachment model and upload routes.
14. Add Activity model and automatic activity logging.
15. Add Notification model and notification creation logic.
16. Add dashboard summary routes.
17. Add report routes.
18. Test all API endpoints.

---

# 23. Frontend Implementation Steps

Follow this order:

1. Set up React with TypeScript.
2. Set up Tailwind CSS.
3. Set up React Router.
4. Create authentication context.
5. Create API client.
6. Build login page.
7. Build register page.
8. Build protected route component.
9. Build main app layout.
10. Build dashboard page.
11. Build ticket list page.
12. Build create ticket page.
13. Build ticket details page.
14. Build edit ticket page.
15. Add comments UI.
16. Add attachment upload UI.
17. Add activity history UI.
18. Add reports page.
19. Add admin users page.
20. Add loading states and error states.

---

# 24. Seed Data

Create seed data for development.

Users:

```text
Admin User - admin@example.com - ADMIN
Manager User - manager@example.com - MANAGER
Agent User - agent@example.com - AGENT
Requester User - requester@example.com - REQUESTER
```

Default password for all seed users:

```text
Password123!
```

Create sample tickets:

1. Change request for adding a new dashboard chart.
2. Task for reviewing monthly reports.
3. Defect for login page error.
4. Defect for broken attachment download.
5. Task for updating user documentation.

---

# 25. Acceptance Criteria

The project is complete when:

* A user can register and log in.
* A user can create a ticket.
* A user can view their tickets.
* Admins and managers can view all tickets.
* Tickets can be assigned.
* Ticket statuses can be updated.
* Change requests can be approved or rejected.
* Users can comment on tickets.
* Users can upload attachments.
* Ticket activity history is recorded.
* Users receive notifications for important updates.
* Dashboard displays ticket statistics.
* Ticket list supports search and filtering.
* Reports page shows basic statistics.
* Role-based permissions work correctly.
* The UI is clean, responsive, and easy to use.

---

# 26. Important Coding Instructions for the AI Agent

When building this project:

* Build the MVP first.
* Do not overcomplicate the system.
* Use clear names for files, functions, components, routes, and database fields.
* Keep backend logic separated into controllers and services.
* Keep frontend components reusable.
* Validate all backend inputs.
* Protect all private API routes.
* Add comments only where useful.
* Make sure the project can run locally.
* Include setup instructions in a README file.
* Include environment variable examples.
* Make the UI clean and simple.
* Do not add advanced features before the core ticket system works.

---

# 27. README Requirements

Create a README file with:

```text
Project name
Project description
Tech stack
Features
Setup instructions
Environment variables
Database setup
How to run backend
How to run frontend
Seed users
API overview
Future improvements
```

---

# 28. Environment Variables

Use these environment variables.

## Backend `.env`

```env
DATABASE_URL="postgresql://username:password@localhost:5432/ticketing_app"
JWT_SECRET="replace_this_with_a_secure_secret"
JWT_EXPIRES_IN="7d"
PORT=5000
UPLOAD_DIR="uploads"
```

## Frontend `.env`

```env
VITE_API_URL="http://localhost:5000/api"
```

---

# 29. Final Deliverable

The final deliverable should be a working full-stack web application with:

* Backend API
* Frontend UI
* Database schema
* Authentication
* Role-based permissions
* Ticket lifecycle management
* Comments
* Attachments
* Activity history
* Notifications
* Dashboard
* Reports
* README setup guide

Build the project in small steps and verify each feature before moving to the next one.
