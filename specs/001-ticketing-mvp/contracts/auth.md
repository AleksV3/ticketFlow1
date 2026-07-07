# Auth & Current User

## `POST /api/auth/login`

No auth required.

**Request**:

```json
{
  "email": "approver@clientco.com",
  "password": "plaintext-from-login-form"
}
```

**Response `200`**:

```json
{
  "token": "eyJhbGciOi...",
  "expiresAt": "2026-07-02T18:15:30Z",
  "user": {
    "id": 41,
    "email": "approver@clientco.com",
    "displayName": "Jane Client",
    "party": "CLIENT",
    "roleId": 7,
    "roleName": "Client Approver",
    "permissions": ["TICKET_READ", "TICKET_CREATE", "COMMENT_PUBLIC_WRITE", "PROPOSAL_APPROVE"],
    "organizationId": 3
  }
}
```

The `permissions` array is the resolved permission set the client uses to show
or hide controls; the server independently enforces the same permissions on
every request, so a hidden control is never the only guard.

**Errors**: `400 VALIDATION_FAILED` (missing fields), `401 UNAUTHENTICATED`
(bad credentials or inactive account — same message for both, standard
practice to avoid leaking which one failed).

## `GET /api/users/me`

**Response `200`**:

```json
{
  "id": 41,
  "email": "approver@clientco.com",
  "displayName": "Jane Client",
  "party": "CLIENT",
  "roleId": 7,
  "roleName": "Client Approver",
  "permissions": ["TICKET_READ", "TICKET_CREATE", "COMMENT_PUBLIC_WRITE", "PROPOSAL_APPROVE"],
  "organizationId": 3,
  "organizationName": "ClientCo"
}
```

`party` is `CLIENT` or `TICKETFLOW1` and is structural — it is never something
a role can grant. `organizationId`/`organizationName` are `null` for
TICKETFLOW1-party users.
