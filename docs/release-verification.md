# Release verification

## Security review

Reviewed 2026-07-16 for Phase 8 T098.

- Cookie authentication uses an `HttpOnly`, `SameSite=Lax` JWT cookie.
- `Secure` defaults to `true`; only the demo profile opts out for plain HTTP
  localhost. Deployments may enforce it with `COOKIE_SECURE=true`.
- Authenticated non-safe methods require Spring Security's CSRF cookie/header
  pair. Login/logout are the only ignored authentication endpoints.
- CORS is credentialed and uses the exact comma-separated
  `APP_CORS_ALLOWED_ORIGINS`; wildcard LAN origins are not accepted.
- JWT secrets have no source default and must contain at least 32 bytes.
- Fixed accounts/passwords occur only in demo V8, whose location is absent
  from the default Flyway profile.
- `npm audit --omit=dev` reports zero vulnerabilities after upgrading Next.js
  and overriding its nested PostCSS to 8.5.10.
- Maven dependencies are managed by Spring Boot 3.5.16; the dependency tree
  was resolved successfully. Re-run organizational SCA before public release.

## T099 clean-install gate

The release gate uses two empty PostgreSQL databases:

1. Start the backend with the default profile against database A. Confirm
   Flyway reaches `7.1`, no V8 row exists, and `app_user` contains zero rows.
2. Start with the demo profile against database B. Confirm Flyway reaches V8,
   with 2 organizations, 6 users, and 3 tickets.
3. Run `cd backend && ./mvnw test` with Docker available.
4. Run `cd frontend && npm audit --omit=dev && npm test && npm run build`.
5. Run `cd frontend && npm run test:e2e` while both servers are running.

Recorded result (2026-07-16): clean production migration stopped at V7.1 with
zero users and no V8 row; clean demo migration reached V8 with 2 organizations,
6 users, and 3 tickets. Hibernate validation passed on PostgreSQL 16. All 53
backend tests (including 12 Testcontainers integration cases), the zero-finding
frontend production audit, 11 frontend tests, type checking, and the Next.js
production build passed.
