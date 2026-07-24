# Feature Specification: Priority Fixes and UX Improvements

**Feature Branch**: `003-priority-fixes-and-ux`

**Created**: 2026-07-24

**Status**: Ready for detailed design

**Input**: High-priority approval and role fixes, followed by dashboard,
field-visibility, administration, ticket-page, navigation, theme, and layout
improvements.

## Delivery Priorities

- **P0 production bug**: restore the existing TASI approval path.
- **P1 authorization and roles**: add organization-wide approval permission and
  ensure roles accept an unrestricted set of distinct catalog permissions.
- **P2 product usability**: editable dashboard and field-level visibility.
- **P3 UI/UX**: ticket layout and filters, team defaults, user selectors,
  workflow administration, full-width layout, light mode, and board contrast.

P0 and P1 MUST be implemented and verified before P2/P3 work begins. The TASI
issue is a domain-flow investigation, not a button-only change.

## User Scenarios & Testing

### User Story 1 - Assigned users can decide TASI approvals (P0)

An assigned approver or required team lead can approve or reject a TASI request
and the ticket moves to the workflow state defined for that decision.

**Acceptance scenarios**:

1. Entering the TASI approval state creates exactly one active approval record
   with the expected ticket, organization, approver/team relationship, and
   pending decision.
2. The resolved designated approver can approve or reject through the protected
   decision endpoint and receives the correct next state.
3. A team lead can decide when the configured workflow requires team-lead
   approval.
4. An unrelated user receives `403 Forbidden`; the decision and ticket state
   remain unchanged.
5. The ticket UI shows enabled decision controls only when the backend advertises
   the command as available to the current actor.
6. Every successful decision records actor, decision, timestamp, optional or
   required reason, status history, and audit in the same transaction.
7. Repeated or stale decisions return `409 Conflict` and do not create duplicate
   history.

### User Story 2 - Authorized administrators can decide any ticket approval (P1)

An administrator can be granted the fixed catalog permission
`APPROVE_ALL_TICKETS`, allowing them to approve or reject any pending approval
inside their authorized organization even when another approver is assigned.

**Acceptance scenarios**:

1. An administrator with `APPROVE_ALL_TICKETS` can decide an approval assigned
   to Developer A.
2. Developer B without that permission remains denied unless normal
   relationship-aware rules authorize Developer B.
3. The override never permits cross-organization access or bypasses the ticket's
   valid pending state, active approval, or protected transition rules.
4. The Role Administration page lists and saves the permission.
5. History and audit identify the actual administrator who made the decision.

### User Story 3 - Administrators manage unrestricted permission sets (P1)

An administrator can attach any number of distinct fixed-catalog permissions to
a role, save them, reload the role, and remove individual permissions without
losing unrelated assignments.

**Acceptance scenarios**:

1. Creating or editing a role with many permissions persists every selection.
2. Reloading the page returns the same complete permission set.
3. Removing one permission removes only that permission.
4. Duplicate permission IDs are rejected or normalized without duplicate rows.
5. Editing a role preserves existing permissions unless the administrator
   explicitly removes them.
6. Concurrent stale role updates return `409 Conflict` rather than silently
   overwriting a newer permission set.

### User Story 4 - Users customize their dashboards (P2)

A user chooses visible widgets and their order, with preferences isolated by
user and organization.

**Acceptance scenarios**:

1. Users can show/hide and reorder supported widgets.
2. Saving and revisiting restores the layout for that user and organization.
3. Another user's layout is unchanged.
4. Reset removes the custom layout and restores current defaults.
5. Unknown or retired widget keys are ignored safely.

Initial widgets are: my open tickets, my team's tickets, tickets by status,
tickets by type, tickets awaiting my approval, and recently updated tickets.

### User Story 5 - Administrators restrict configurable fields by role (P2)

An administrator configures which roles may view, edit, and optionally set a
dynamic subtype field during creation.

**Acceptance scenarios**:

1. With no visibility rules configured, existing behavior is preserved.
2. Restricted fields and values are omitted from create references, ticket
   detail/list responses, and edit forms for unauthorized users.
3. Direct API attempts to set or update unauthorized fields return `403` or a
   field-level validation error without changing stored values.
4. Read and write authorization is recalculated server-side from current roles.
5. Field configuration remains manageable by authorized field administrators.
6. Role deletion or deactivation cannot create an accidental allow-all rule.

### User Story 6 - Users work with clearer ticket pages and filters (P3)

Ticket details remain readable at desktop and mobile widths. Comments and
attachments each occupy separate full-width rows. Text search remains visible,
while advanced filters are collapsed by default and change results only after
the user chooses Apply.

**Acceptance scenarios**:

1. Comments and attachments never render side by side.
2. Opening, editing, or clearing draft filters does not request new results
   until Apply; Clear resets controls and applies the unfiltered state.
3. Applied filter state is visibly distinct from draft filter state.
4. Supported filters include type, status, priority, assignee, team, creator,
   date range, workflow, and approval status where the backend supports them.
5. Controls are keyboard accessible and responsive.

### User Story 7 - Team and user administration starts with useful context (P3)

The Teams page opens the current user's last-viewed assigned team, falling back
to their first assigned team. User assignment screens provide a searchable,
keyboard-accessible combobox and card selection pattern.

**Acceptance scenarios**:

1. A last-viewed team is restored only if the user is still a member.
2. Without a valid saved team, the first assigned team opens.
3. A user with no teams sees a clear empty state.
4. Last-viewed state is isolated per user.
5. User cards show name, email, role, and selection state, with search/filter
   controls and keyboard operation.

### User Story 8 - Workflow administration is contextual and safe (P3)

Workflow actions appear only in their valid context, subtype administration
opens only after explicit type selection, and administrators rename workflow
states without recreating them.

**Acceptance scenarios**:

1. “Create ticket type for selected workflow” appears below the canvas only
   while editing a persisted workflow.
2. Subtype forms do not open automatically and require selecting a type in Type
   Administration.
3. Renaming a state rejects blank and duplicate names.
4. Rename preserves the state identity and all incoming/outgoing transitions.
5. A successful rename creates configuration audit history.

### User Story 9 - Users can choose a readable application theme and layout (P3)

Wide data-heavy pages use available desktop width. Users can select light or
dark mode; in the absence of a preference, the system theme is used.

**Acceptance scenarios**:

1. Tables, boards, dashboards, and workflow canvases use available width while
   long-form text retains a readable measure.
2. Theme selection persists per user; before login or before a server preference
   loads, system preference is used.
3. Light and dark themes meet WCAG 2.1 AA contrast for normal text and controls.
4. Team-board descriptions remain readable, visually secondary, and correctly
   truncated in both themes.

## Functional Requirements

- **FR-001**: Diagnose and correct the full TASI approval path: state entry,
  approval creation, approver resolution, authorization, command exposure,
  decision execution, and next-state transition.
- **FR-002**: Add fixed catalog permission `APPROVE_ALL_TICKETS`.
- **FR-003**: Approval authorization MUST allow either the normal resolved
  relationship rule or `APPROVE_ALL_TICKETS`, always combined with tenant scope.
- **FR-004**: Approval decisions MUST remain protected domain commands and
  atomic with approval, transition, history, and audit writes.
- **FR-005**: Role permission sets MUST have no artificial count limit and MUST
  enforce uniqueness.
- **FR-006**: Role updates MUST use replace-set semantics with concurrency
  protection and preserve permissions sent in the authoritative request.
- **FR-007**: Store dashboard preferences by user and organization using a
  bounded developer-owned widget catalog.
- **FR-008**: Store dynamic-field view/edit/create role rules and enforce them
  during response shaping and mutation validation.
- **FR-009**: Absence of field rules MUST preserve current visibility behavior.
- **FR-010**: Ticket filter controls MUST separate draft and applied state.
- **FR-011**: Persist the last-viewed team per user and validate membership
  before restoring it.
- **FR-012**: Reuse one accessible searchable selection pattern for user and
  ticket-type selection.
- **FR-013**: Workflow-state rename MUST update the display name in place and
  preserve stable state identity and transitions.
- **FR-014**: User preferences MUST be tenant/user scoped and must not contain
  executable or administrator-defined UI code.
- **FR-015**: All business and configuration mutations MUST be audited according
  to the constitution.
- **FR-016**: Responsive layouts and both themes MUST meet keyboard, focus, and
  contrast accessibility requirements.

## Edge Cases

- The assigned approver is inactive, removed from the team, or belongs to a
  different organization.
- An override administrator loses permission after loading the ticket.
- Two actors decide the same approval concurrently.
- A role request contains unknown, cross-organization, or duplicate permission
  IDs.
- A user preference references a deleted widget, team, role, or field.
- A user holds multiple roles with mixed field rules: permissions are additive;
  any applicable allow grants the operation, while tenant scope still applies.
- A field is visible but not editable, or editable only during create.
- A workflow state is renamed while another administrator edits the workflow.
- Theme preference cannot be loaded; the application remains usable using the
  system/default theme.

## Success Criteria

- **SC-001**: Automated tests execute complete TASI approve and reject paths for
  assigned approver, required team lead, global approver, unrelated user, stale
  decision, and cross-organization user.
- **SC-002**: Every successful approval test asserts one decision, one valid
  state transition/history event, and an attributable audit record.
- **SC-003**: A role containing the full permission catalog round-trips without
  loss or duplication.
- **SC-004**: Dashboard, team, and theme preferences survive reload and remain
  isolated across two users and two organizations.
- **SC-005**: Field visibility integration tests prove restricted values are
  absent from responses and cannot be changed through direct API calls.
- **SC-006**: Target P3 pages pass responsive keyboard review and automated
  accessibility/contrast checks in light and dark themes.

## Assumptions and Non-goals

- The permission catalog remains developer-owned; administrators only map its
  entries to roles.
- “Any ticket” means any pending approval within the actor's authorized
  organization scope, never a cross-tenant bypass.
- Existing relationship-aware approval remains the default and is not replaced.
- Dashboard customization is limited to the initial widget catalog; arbitrary
  user-authored widgets are out of scope.
- Filter customization means choosing from supported filters, not defining
  arbitrary database queries.
- This feature does not redesign workflows or subtype schemas introduced by
  feature `002`.
