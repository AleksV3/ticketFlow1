# User Preference Contracts

Preferences are scoped to the authenticated user and current organization
context. A caller cannot supply a different user ID.

- `GET /api/preferences`
- `PUT /api/preferences`
- `DELETE /api/preferences` resets current-scope preferences

```json
{
  "dashboardWidgets": [
    "MY_OPEN_TICKETS",
    "AWAITING_MY_APPROVAL",
    "RECENTLY_UPDATED"
  ],
  "enabledTicketFilters": ["TYPE", "STATUS", "TEAM"],
  "lastViewedTeamId": 12,
  "theme": "LIGHT",
  "version": 3
}
```

Unknown widget/filter/theme values return `400`. An invalid or no-longer-
assigned team is ignored on read and returns `409` on write. Stale versions
return `409`. Reset is idempotent and restores developer-owned defaults.
