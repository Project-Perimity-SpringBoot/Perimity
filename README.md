# Perimity

Smart Campus Access & Gate Pass Management System

Replaces the paper gate register with a digital, forgery-proof, searchable
system. Entry-only scanning at the gate.

Perimity is **campus-agnostic**. It is not built for any one institution. Campus
names, gates, and departments are all data supplied by each campus during
onboarding — none of it is hardcoded.

## Architecture

Six backend microservices plus a React frontend. Each service owns its own
database — services never read another service's database directly, only call
its API.

| Service | Folder | Database | Port |
|---|---|---|---|
| Auth Service | `auth-service/` | authdb (PostgreSQL) | 8081 |
| User Service | `user-service/` | userdb (PostgreSQL) | 8082 |
| Gate Pass Service | `gatepass-service/` | gatepassdb (PostgreSQL) | 8083 |
| Campus Service | `campus-service/` | campusdb (PostgreSQL) | 8084 |
| Guard Service | `guard-service/` | entrylogdb (MongoDB) | 8085 |
| QR Service | `qr-service/` | qrdb (PostgreSQL) | 8086 |
| Frontend | `frontend/` | — (React) | 3000 |

All five PostgreSQL databases run on **one** Postgres container in development
(port 5432) to keep memory use low on a single machine. The database-per-service
boundary is still enforced in code: each service connects only to its own
database name and holds no credentials for any other.

Shared infrastructure: RabbitMQ (async jobs — QR/PDF generation, bulk uploads,
emails), Redis (caching active-pass lookups for sub-second gate scans), S3 or
MinIO (file storage — the database stores only the object key, never the file).

## Roles

Six user classes. **Login method differs by role — this is not the same for
everyone.**

| Role | Login | Scope |
|---|---|---|
| Super Admin | Password | Creates and suspends campuses, creates Campus Admins, views platform-wide stats |
| Campus Admin | Password | Manages one campus: faculty, guards, gates, departments, policy |
| Faculty | Password **or** OTP | Approves student and visitor requests, bulk upload |
| Student | Password **or** OTP | Profile, daily pass, event passes |
| Visitor | OTP only | One-time or event guest, no password ever |
| Guard | Password | Bound to one gate per session, scans passes |

## Docs

Governing documents live in `/docs`. Read the ones relevant to your service
before writing code.

- `Perimity_SRS.pdf` — the formal IEEE-830 requirements spec, authoritative
- `Perimity_SRS_v1.1_Amendments.md` — additions and corrections to apply
- `Perimity_Database_Design.pdf` — per-service schema
- `Perimity_Event_Bulk_Design.pdf` — event and bulk-onboarding logic
- `Perimity-Complete-Roadmap.pdf` — 25-day sprint plan

## Rules that apply to every service

- **No institution names anywhere.** No campus name, department list, or email
  domain may appear in code, seed data, config, or UI copy. Campus name and logo
  render from the API response for the logged-in user's campus.
- **Departments are campus data**, created by a Campus Admin after onboarding.
  Do not seed a department list in any migration script.
- **No `Semester` field in any UI form**, even if present in a schema. Academic
  scheduling data is not needed for access control.
- **Entry only.** No exit scan, no in/out toggle, anywhere in backend or UI.
- **Files go to object storage** — the database stores only the object key.
- **Passwords: bcrypt. OTPs: SHA-256. QR tokens: AES-256.** Never store any of
  these in plain text. No personal data inside a QR code.
- **Every secret comes from an environment variable.** Nothing sensitive is
  committed, including in `application.yml`.

## Running locally

```
cp .env.example .env
```

Fill in the real values in `.env`, then:

```
docker compose up --build
```

This starts Postgres, MongoDB, RabbitMQ, and Redis, and creates all five
PostgreSQL databases automatically via `docker/postgres/init-databases.sql`.

Service containers are commented out in `docker-compose.yml`. Uncomment your
service's block once you have a `Dockerfile` in your folder.

> If you ran an older `docker compose up` before this change, the Postgres
> volume already exists and the init script will **not** re-run. Reset it once
> with `docker compose down -v`, then bring it back up. This deletes local data.

## Branch workflow

- Never push directly to `main`.
- Branch naming: `feature/<service>-<short-description>`, e.g.
  `feature/auth-otp-login`.
- Open a PR into `main`, get at least 1 review, then merge.
