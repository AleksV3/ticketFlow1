# Comments

Comment visibility is a fixed set: `INTERNAL` or `PUBLIC`.

## `GET /api/tickets/{ticketKey}/comments`

Requires `TICKET_READ`. **Response `200`**: array of `Comment`, ordered by
`createdAt` ascending. `INTERNAL` comments are silently omitted from the
response for callers who lack `COMMENT_INTERNAL_WRITE` (FR-012) — not returned
with a redaction marker, simply absent, so the frontend never has to remember
to hide them. Client-side default roles do not hold that permission.

```json
[
  {
    "id": 512,
    "author": { "id": 58, "displayName": "Alex TicketFlow1" },
    "body": "Reproduced locally, root cause identified.",
    "visibility": "INTERNAL",
    "createdAt": "2026-07-01T10:00:00Z"
  }
]
```

## `POST /api/tickets/{ticketKey}/comments`

Requires `COMMENT_PUBLIC_WRITE` to post a `PUBLIC` comment; posting an
`INTERNAL` comment additionally requires `COMMENT_INTERNAL_WRITE`.

**Request**:

```json
{ "body": "We've confirmed the fix resolves the issue.", "visibility": "PUBLIC" }
```

A caller without `COMMENT_INTERNAL_WRITE` who sends `visibility=INTERNAL` is
rejected with `403 FORBIDDEN` — client-side default roles do not hold that
permission, so they may only post `PUBLIC`.

**Response `201`**: created `Comment`. Writes a `COMMENT_ADDED` audit log
entry (FR-013).
