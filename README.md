# Company Ops Demo (Backend)

Spring Boot backend for a public portfolio demo of an internal expense workflow.

This backend is designed for an **open demo**:
- Seeded accounts
- Public reset/seed endpoint
- Demo-friendly auth (login by email)

## Workflow overview (current)

### Statuses
- `DRAFT`
- Normal approval chain:
  - `MANAGER_REVIEW` → `CFO_REVIEW` → (sometimes) `CEO_REVIEW`
- Policy exception flow:
  - `CFO_SPECIAL_REVIEW` / `CEO_SPECIAL_REVIEW` → `CHANGES_REQUESTED`
- Final:
  - `APPROVED` / `REJECTED`

### Submit routing
When a submitter posts `POST /api/expense-reports/{id}/submit`:
- If **no policy warnings** remain → routes into the normal approval chain.
- If **policy warnings exist** → creates/updates an exception review record and routes the report to:
  - `CFO_SPECIAL_REVIEW` (default)
  - `CEO_SPECIAL_REVIEW` (when the submitter is CFO)

### Exception review (API path kept for compatibility)
Exception review uses these endpoints:
- `GET  /api/expense-reports/{id}/special-review`
- `POST /api/expense-reports/{id}/special-review/decide`

Rules:
- Reviewer role must match the review state (CFO vs CEO).
- If **any item is rejected**:
  - each rejected item requires `financeReason`
  - request requires a non-empty `reviewerComment`
  - report becomes `CHANGES_REQUESTED` (submitter can edit + resubmit)
- If **all items are approved**:
  - exception review data is cleared
  - report routes back into the normal approval chain

## Local development

### Requirements
- Java 17

### Run
```bash
./mvnw spring-boot:run
```

Default local config uses H2 (in-memory).
H2 console (dev only): `http://localhost:8080/h2-console`

## Public demo endpoints
- `POST /api/demo/reset` — wipes + seeds demo users and sample reports
- `POST /api/auth/login` — demo-friendly login by email

## Profiles

### `default` (local/dev)
- H2 in-memory
- CORS: `*` (demo-friendly)
- scheduled reset disabled

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

> Public demo note: reset endpoint + open CORS are for demo convenience. Tighten these for any real production environment.
