# Attachments

Metadata-only for MVP (spec Assumptions) — no file bytes are stored or
served. These records are attachment references, not proof that a file was
uploaded; real binary upload will use a separate endpoint after MVP.

## `GET /api/tickets/{ticketKey}/attachments`

Requires `TICKET_READ`. **Response `200`**:

```json
[
  {
    "id": 88,
    "uploadedBy": { "id": 41, "displayName": "Jane Client" },
    "fileName": "error-screenshot.png",
    "contentType": "image/png",
    "sizeBytes": 245678,
    "createdAt": "2026-07-01T09:05:00Z"
  }
]
```

## `POST /api/tickets/{ticketKey}/attachments`

Requires `TICKET_UPDATE`.

**Request**:

```json
{ "fileName": "error-screenshot.png", "contentType": "image/png", "sizeBytes": 245678 }
```

**Response `201`**: created `Attachment` metadata record. Writes an
`ATTACHMENT_ADDED` audit log entry.

`fileName` is 1–255 characters, `contentType` is a MIME-shaped value up to 100
characters, and `sizeBytes` must be nonnegative and no larger than the
configured metadata limit. Every read/write first resolves the parent ticket
through the caller's organization scope.
