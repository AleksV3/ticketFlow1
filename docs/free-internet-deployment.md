# Free internet deployment: Cloud Run + Neon + Vercel

This project no longer depends on Render for deployment.

Recommended free stack:

- Backend API: Google Cloud Run
- Database: Neon Postgres
- Frontend: Vercel

## Current Neon database

- Neon project: `ticketFlow1`
- PostgreSQL version verified: `16.14`
- Flyway schema verified through migration: `24 - add workflow canvas layout`
- Local Neon env file: `.env.local`

`.env.local` is intentionally gitignored because it contains database secrets.

## Backend: Cloud Run

The backend is a Spring Boot app in [`backend/`](../backend).

It is already configured to:

- listen on Cloud Run's `$PORT`
- default to local port `8081` when running locally
- accept Neon env vars:
  - `DATABASE_URL`
  - `DATABASE_USERNAME`
  - `DATABASE_PASSWORD`

### Required Cloud Run environment variables

Set these in Cloud Run → service → Edit and deploy new revision → Variables & Secrets:

```text
DATABASE_URL=jdbc:postgresql://<neon-host>/<neon-database>?sslmode=require
DATABASE_USERNAME=<neon-user>
DATABASE_PASSWORD=<neon-password>
JWT_SECRET=<long-random-secret>
COOKIE_SECURE=true
APP_CORS_ALLOWED_ORIGINS=https://<your-vercel-domain>
ATTACHMENT_STORAGE_DIRECTORY=/tmp/ticketflow1-attachments
APP_DEV_LOGGING_ENABLED=true
```

Do not commit real values.

### Deploy command if Google Cloud CLI is installed

From the repo root:

```bash
gcloud run deploy ticketflow1 \
  --source backend \
  --region europe-west12 \
  --allow-unauthenticated \
  --set-env-vars COOKIE_SECURE=true,ATTACHMENT_STORAGE_DIRECTORY=/tmp/ticketflow1-attachments,APP_DEV_LOGGING_ENABLED=true
```

Add the database and JWT variables in the Cloud Console UI or use Secret Manager.

After deploy, verify:

```bash
curl https://<cloud-run-url>/api/health
```

Expected result: backend health response, not the Cloud Run placeholder HTML page.

## Frontend: Vercel

Import the GitHub repo into Vercel.

Settings:

```text
Root Directory: frontend
Build Command: npm run build
Framework: Next.js
```

Environment variables:

```text
NEXT_PUBLIC_API_BASE_URL=https://<cloud-run-url>/api
NEXT_PUBLIC_ENABLE_DEV_LOGS=true
```

After Vercel gives the frontend URL, update Cloud Run:

```text
APP_CORS_ALLOWED_ORIGINS=https://<your-vercel-domain>
```

Then deploy a new Cloud Run revision.

## Important limitation: attachments

Cloud Run's filesystem is temporary. Attachments stored under `/tmp` can disappear after the backend restarts.

For a real public version, move attachments to one of:

- Google Cloud Storage
- Neon Object Storage
- Supabase Storage

Until then, ticket data is persistent in Neon, but attachment files are not guaranteed to survive restarts.

## Verified locally

Using the ignored Neon `.env.local`:

- backend connected to Neon
- `/api/health` returned `200`
- Flyway latest applied migration is `24`
