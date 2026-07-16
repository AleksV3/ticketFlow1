# Operations and known limitations

## PostgreSQL backup and restore

Create a compressed logical backup from the Compose database:

```bash
mkdir -p backups
docker compose exec -T postgres pg_dump \
  -U ticketflow1 -d ticketflow1_ticketing -Fc > backups/ticketflow1.dump
```

Keep backups encrypted, access-controlled, and outside the application host.
Test restores regularly; a backup that has never been restored is unverified.

Restore into a new empty database to avoid mixing old and restored rows:

```bash
docker compose exec -T postgres createdb -U ticketflow1 ticketflow1_restore
docker compose exec -T postgres pg_restore \
  -U ticketflow1 -d ticketflow1_restore --clean --if-exists < backups/ticketflow1.dump
```

Stop application writes during a final production backup/restore window. Point
the backend at the restored database, start it, let Flyway validate/apply newer
migrations, then run login, ticket read, and organization-isolation smoke tests
before switching traffic. Retain the original database until acceptance.

## Known MVP limitations

- Attachments store metadata/reference paths, not uploaded file bytes or virus scanning.
- SLA calculations use a simplified business-hours model without holiday calendars.
- Workflow changes are additive; breaking migration of in-flight tickets is unsupported.
- JWT role changes take effect at the next login; there is no token revocation list.
- Password reset, MFA, SSO, self-registration, and account lockout are not implemented.
- Tenant self-service onboarding and per-tenant encryption keys are outside the MVP.
- Search targets pilot scale rather than enterprise full-text workloads.
- Email, webhooks, scheduled escalation, and background jobs are outside the MVP.
- Audit records have no external WORM archive or automated retention policy.
- Compose is a single-node local/demo topology without TLS, HA, or monitoring.
