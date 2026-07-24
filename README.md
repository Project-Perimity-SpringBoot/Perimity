# Perimity

Smart Campus Access & Gate Pass Management System — CDAC Mumbai team project.

Replaces the paper gate register with a digital, forgery-proof, searchable system.
Entry-only. Passwordless (email + OTP) login for everyone — visitors, students,
faculty, and admins.

## Architecture

Microservices. Each service owns its own database — services never read another
service's database directly, only call its API.

| Service | Folder | Database | Port |
|---|---|---|---|
| Auth Service | `auth-service/` | AuthDB (PostgreSQL) | 5432 |
| User Service | `user-service/` | UserDB (PostgreSQL) | 5433 |
| Gate Pass Service | `gatepass-service/` | GatePassDB (PostgreSQL) | 5434 |
| Campus Service | `campus-service/` | CampusDB (PostgreSQL) | 5435 |
| Guard Service | `guard-service/` | EntryLogDB (MongoDB) | 27017 |
| QR Service | `qr-service/` | QRDB (PostgreSQL) | 5436 |
| Frontend | `frontend/` | — (React) | 3000 |

Shared infrastructure: RabbitMQ (async jobs — QR/PDF generation, bulk uploads,
emails), Redis (caching), AWS S3 (file storage — DB only stores S3 keys, never
files).

## Docs

Full requirements and design decisions live in `/docs`:
- `SRS_v1.0.md` — requirements
- `Perimity_Database_Design.md` — per-service schema
- `Perimity_Event_Bulk_Design.md` — event & bulk-onboarding logic

**Read the relevant doc before writing code for your service.**

## Rules that apply to every service

- No `Semester` field in any UI, even if present in a schema — CDAC Mumbai has
  no semester concept.
- Login is passwordless everywhere (email + OTP).
- Files (photos, Aadhaar docs, QR images, PDFs) go to S3 — DB stores only the
  S3 key.
- Passwords: bcrypt. OTPs: SHA-256. QR tokens: AES-256. Never store any of
  these in plain text.

## Running locally

```
docker-compose up --build
```

This starts all six services plus Postgres, MongoDB, RabbitMQ, and Redis.

## Branch workflow

- Never push directly to `main`.
- Branch naming: `feature/<service>-<short-description>`, e.g.
  `feature/auth-otp-login`.
- Open a PR into `main`, get at least 1 review, then merge.
