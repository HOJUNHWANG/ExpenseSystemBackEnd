# Company Ops Demo (Backend)

Spring Boot backend for a public portfolio demo of an internal expense workflow.

## Workflow overview
- Employee creates report (DRAFT)
- Submit:
  - If no policy warnings → SUBMITTED (normal manager queue)
  - If policy warnings → employee must provide per-warning reasons → FINANCE_SPECIAL_REVIEW
- Finance special approval:
  - All approve → SUBMITTED and exception data is cleared
  - Any reject → CHANGES_REQUESTED (submitter can edit and resubmit)

## Local development

### Requirements
- Java 17

### Run
```bash
./mvnw spring-boot:run
```

Default local config uses H2 (in-memory).\
H2 console (dev only): `http://localhost:8080/h2-console`

## Public demo endpoints
- `POST /api/demo/reset` — wipes + seeds demo users and sample reports
- `POST /api/auth/login` — demo-friendly login by email

## Profiles

### `default` (local/dev)
- H2 in-memory
- CORS: `*` (demo-friendly)
- nightly reset disabled

### `prod` (Render)
Configured via `application-prod.yml`.

Required environment variables:
- `SPRING_PROFILES_ACTIVE=prod`
- `SPRING_DATASOURCE_URL` (JDBC url)
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

Optional:
- `APP_CORS_ALLOWED_ORIGINS` (comma-separated allowlist; default `*`)
- `DEMO_RESET_ENABLED` (default true)
- `DEMO_RESET_CRON` (default midnight)
- `DEMO_RESET_ZONE` (default America/New_York)

## Deployment (Render)
This repo includes a `Dockerfile`.

Suggested settings:
- Runtime: Docker
- Build: (Render will build from Dockerfile)
- Start: (from Dockerfile)

After deployment:
- Confirm `POST /api/demo/reset` works
- Set `APP_CORS_ALLOWED_ORIGINS` to your Vercel URL(s)

> Public demo note: reset endpoint + open CORS are for demo convenience.
> Tighten these for any real production environment.
