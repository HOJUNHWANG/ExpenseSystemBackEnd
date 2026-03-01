# Expense System — Backend

![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?logo=springboot)
![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk)
![JWT](https://img.shields.io/badge/JWT-jjwt_0.12-black?logo=jsonwebtokens)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?logo=postgresql)
![Swagger](https://img.shields.io/badge/Swagger_UI-springdoc-85EA2D?logo=swagger)

Spring Boot REST API for a corporate expense management workflow — JWT-authenticated, multi-role approval chain, and policy enforcement engine.

## API Overview

Interactive docs: `http://localhost:8080/swagger-ui.html`

| Group | Endpoints |
|---|---|
| **Auth** | `POST /api/auth/login` |
| **Reports** | CRUD + submit + search + pagination |
| **Approvals** | Approve / reject per role |
| **Policy exceptions** | Special review decide |
| **Dashboard** | Stats, recent activity |
| **Demo** | `POST /api/demo/reset` (re-seed) |

## Workflow

```
DRAFT
  │ submit (no warnings)
  ▼
MANAGER_REVIEW → CFO_REVIEW → (CEO_REVIEW) → APPROVED
                                              └─ or REJECTED

  │ submit (policy warnings)
  ▼
CFO_SPECIAL_REVIEW / CEO_SPECIAL_REVIEW
  │ all approved          │ any rejected
  ▼                       ▼
normal chain         CHANGES_REQUESTED → re-submit
```

## Local Development

### Requirements
- Java 17 (JDK)

### Run

```bash
# Windows
mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

Default config uses H2 in-memory database.
H2 console (dev only): `http://localhost:8080/h2-console`

### Run Tests

```bash
mvnw.cmd test      # Windows
./mvnw test        # macOS / Linux
```

Tests include:
- **PolicyEngineTest** — 8 pure unit tests (no Spring context)
- **ExpenseReportServiceTest** — 6 Mockito-based service tests

## Environment Variables

| Variable | Default | Required in prod | Description |
|---|---|---|---|
| `APP_JWT_SECRET` | `dev-secret-...` | **Yes** | HS256 signing secret (≥ 32 chars) |
| `SPRING_DATASOURCE_URL` | H2 in-memory | **Yes** | JDBC URL for PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | `sa` | **Yes** | DB username |
| `SPRING_DATASOURCE_PASSWORD` | _(empty)_ | **Yes** | DB password |
| `APP_CORS_ALLOWED_ORIGINS` | `*` | Recommended | Comma-separated origin allowlist |
| `DEMO_RESET_ENABLED` | `false` | No | Enable scheduled demo data reset |
| `DEMO_RESET_CRON` | `0 0 0 * * *` | No | Cron expression for reset schedule |

## Architecture

```
controller/
  AuthController       ← /api/auth/login (JWT)
  ExpenseReportController
  DemoController

service/
  ExpenseReportService ← business logic, status transitions
  PolicyEngine         ← stateless rule evaluation
  DemoDataService      ← seed data

config/
  JwtUtil              ← HS256 token generation & validation
  JwtAuthFilter        ← OncePerRequestFilter — reads Bearer token
  SecurityConfig       ← stateless, CSRF off, permit auth/docs paths
  WebConfig            ← CORS configuration
  BusinessConstants    ← policy limits (BigDecimal)

domain/
  ExpenseReport, ExpenseItem, User, AuditLog, SpecialReview
```

## Security

- **JWT (jjwt 0.12.6)** — HS256, 24 h TTL, claims: `userId`, `name`, `email`, `role`
- **BCrypt** — demo password hashed with `BCryptPasswordEncoder`
- **Stateless** — no server-side sessions; every request is authenticated by token
- H2 console and Swagger UI are permit-listed (dev only; tighten in prod)

## Demo Accounts

All accounts use password **`demo1234`**.

| Email | Role |
|---|---|
| `jun@example.com` | EMPLOYEE |
| `manager@example.com` | MANAGER |
| `finance@example.com` | CFO |
| `ceo@example.com` | CEO |

## Deployment (Render)

Includes a `Dockerfile`. Set the environment variables listed above and deploy.

After deployment, call `POST /api/demo/reset` to seed initial data.

> Public demo note: the reset endpoint and open CORS are for demo convenience. Restrict these for any real production environment.
