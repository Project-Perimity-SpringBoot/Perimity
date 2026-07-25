# Perimity — Team Build Guide

**Per-service data, APIs, screens, and testing method**
Six-person team · Six microservices · Feature-branch workflow

Version 1.1

> **This is not the SRS.** The authoritative requirements document is
> `Perimity_SRS.pdf` (formal IEEE-830, with FR-xxx codes), plus
> `Perimity_SRS_v1.1_Amendments.md`. This guide is the practical build companion:
> it tells each member exactly what to build, in what order, and how to test it.
>
> An earlier version of this file was named `Perimity_SRS_v1.1.pdf`, which
> collided with the formal SRS version number and sent people to two different
> specs. It has been renamed.

Read alongside: `Perimity_Database_Design.md`, `Perimity_Event_Bulk_Design.md`.

---

## 1. Purpose and Scope

This document tells each team member exactly what their service must do — its data, its API, its screens, and its testing method — so six people can build in parallel without stepping on each other.

Perimity replaces the paper gate register with a digital, forgery-proof, searchable system. Entry-only. Supports standing member passes and time-boxed event passes, bulk onboarding via Excel, and QR-based gate scanning with instant GREEN / RED / AMBER results.

Perimity is **campus-agnostic**. It is not built for any institution. Campus names, gates, and departments are all data supplied at onboarding.

### Glossary

| Term | Meaning |
|------|---------|
| Identity | Who someone is — one per person, forever, keyed by email |
| Pass | Permission to enter, for a purpose, for a time window. One identity can hold several at once |
| DAILY pass | Standing pass for students and faculty, no end date |
| EVENT pass | Time-boxed pass tied to a specific event's date range |
| OTP | One-time password, emailed, six digits, ten-minute expiry |
| Guard scan | A QR scan at a gate; always returns GREEN, RED, or AMBER |
| Department | A campus-defined grouping, created by that campus's admin. There is no fixed list and no semester concept |

---

## 2. Overall Description

### 2.1 Product perspective

Six independent Spring Boot services, each with its own database, each exposing a REST API documented via Swagger. A React frontend calls these APIs. RabbitMQ handles anything slow or asynchronous (QR and PDF generation, bulk-upload processing, emails). Redis caches active-pass lookups so a gate scan returns in under a second. No service reads another service's database — only its API.

An API Gateway sits in front of the services and validates the JWT on every protected request. During early development, before the gateway exists, each React page may call its own backend directly at its own port. The gateway is added once services stabilise; the routes below do not change when it appears.

### 2.2 User classes

Six roles. **Login differs by role** — this is not passwordless for everyone.

| Role | Password | OTP | Can do |
|------|----------|-----|--------|
| Super Admin | yes | no | Create and suspend campuses, create Campus Admins, view platform-wide statistics |
| Campus Admin | yes | no | Manage one campus: faculty, guards, gates, departments, policy, blocklist |
| Faculty | yes | yes | Approve student and visitor requests, bulk upload, run events |
| Student | yes | yes | Profile, daily pass, event passes |
| Visitor | never | yes | Register for a visit, verify by OTP, view and download their pass |
| Guard | yes | no | Bound to one gate per session, scan QR, see GREEN / RED / AMBER |

Event organiser is not a separate role — it is a Faculty or Campus Admin who created an event and can see its attendance dashboard.

### 2.3 Operating environment

Docker Compose locally: all six services plus PostgreSQL, MongoDB, RabbitMQ, and Redis. All five PostgreSQL databases share one Postgres container in development, created at startup by `docker/postgres/init-databases.sql`. Cloud deployment on EC2 with S3 later.

### 2.4 Design constraints — apply to every service, no exceptions

- **No institution name, department list, or email domain** in code, seed data, config, or UI text. Campus data comes from the API.
- **No `Semester` field in any UI**, even if a schema has one. Not needed for access control.
- **Entry only.** No exit scan, no in/out toggle, in backend or UI.
- Files live in object storage; the database stores only the key.
- Passwords bcrypt. OTPs SHA-256. QR tokens AES-256. No personal data inside a QR code.
- Every secret comes from an environment variable. Nothing sensitive is committed.
- Every service exposes Swagger UI — see Section 7. This is how teammates integrate before any frontend exists.
- No service calls another service's database directly.
- Nothing is hard-deleted. Deactivate accounts, suspend campuses, retain expired passes.

---

## 3. System Architecture Overview

| Service | Database | Type | Service port | Owner |
|---------|----------|------|--------------|-------|
| Auth Service | `authdb` | PostgreSQL | 8081 | Omkar |
| User Service | `userdb` | PostgreSQL | 8082 | Mukul |
| Gate Pass Service | `gatepassdb` | PostgreSQL | 8083 | Tushar |
| Campus Service | `campusdb` | PostgreSQL | 8084 | Arham |
| Guard Service | `entrylogdb` | MongoDB | 8085 | Palash |
| QR Service | `qrdb` | PostgreSQL | 8086 | Sanjay |

**Ownership model.** There is no separate frontend person. Whoever owns a service owns its backend API and the React pages that call it. Four screens are cross-assigned to balance load — see Section 6.

---

## 4. Functional Requirements by Service

Each section is self-contained. A teammate needs their own section, Sections 1–2 and 6–7, and the two companion design docs, to start building.

### 4.1 Auth Service — `authdb` (port 8081) — Omkar

**Entities:** `users`, `otp_verifications`, `password_resets`, `blocklist`, `audit_logs`.

**Requirements covered:** FR-REG-1…9, FR-AUTH-1…7, FR-BLK-1…6, FR-AUD-1…5, FR-SESS-1…7.

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/auth/register` | Visitor or student self-registration; screens the blocklist |
| POST | `/api/auth/otp/request` | `{email, purpose}` → generates OTP, emails it, stores SHA-256 hash |
| POST | `/api/auth/otp/verify` | `{email, otp}` → validates, returns JWT |
| POST | `/api/auth/login` | `{email, password}` → JWT. All staff roles |
| POST | `/api/auth/logout` | Invalidates the session, writes an audit entry |
| POST | `/api/auth/password/forgot` | Emails a single-use, time-limited reset link |
| POST | `/api/auth/password/reset` | `{token, newPassword}` |
| POST | `/api/auth/password/change` | First-login change for admin-created accounts |
| GET | `/api/auth/me` | Current user's basic info and role |
| GET | `/api/auth/blocklist` | Campus Admin — paginated, searchable |
| POST | `/api/auth/blocklist` | Add an email or phone with a mandatory reason |
| DELETE | `/api/auth/blocklist/{id}` | Remove an entry |
| GET | `/api/auth/audit-logs` | Paginated audit trail; Super Admin sees all campuses, Campus Admin sees their own |
| POST | `/api/internal/auth/users` | Internal — called when another service creates an identity |

**Screens built here:** 1 Email Entry, 2 OTP Verify, plus the **shared shell** (routing, AuthContext, ProtectedRoute, Navbar, Sidebar, Toast).

The login page needs a password field **and** a "log in with OTP instead" option shown to Faculty and Student only. Visitors see the email + OTP flow and never a password field.

**Business rules.** OTP expires in 10 minutes, locks after 5 wrong attempts, is rate-limited to 3 requests per email per 15 minutes, and is never stored or logged in plain text. Accounts lock after repeated failed logins. Blocklisting someone must also revoke their active passes by calling gatepass-service.

> The shared shell is a **hard Day 13 deadline** — all five other members build inside it.

### 4.2 User Service — `userdb` (port 8082) — Mukul

**Entities:** `student_profiles`, `faculty_profiles`, `departments`, `documents`.

**Requirements covered:** FR-PROF-1…9.

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/users/students/{id}` | Fetch one student profile |
| POST | `/api/users/students` | Create a student profile (calls Auth Service first for the identity) |
| PUT | `/api/users/students/{id}` | Update. No semester field. Sensitive change pauses the pass |
| GET | `/api/users/faculty/{id}` | Fetch one faculty profile |
| POST | `/api/users/faculty` | Create a faculty profile |
| GET | `/api/users/departments` | List departments **for the caller's campus** |
| POST | `/api/users/departments` | Campus Admin creates a department |
| POST | `/api/users/documents` | Register an uploaded document's object key and metadata |

**Screens built here:** 3 Student Profile, 4 Faculty Profile, 5 Student Directory, and **17 Blocklist** (which calls auth-service).

Also owns the reusable **department picker** component — populated from the API, no free-text entry — and the **document upload widget**.

**Business rules.** No `semester` anywhere. Departments are never hardcoded or seeded. Photos JPEG/PNG up to 2 MB, documents PDF/JPEG/PNG up to 5 MB, content type validated server-side. Object keys are generated server-side. Changing name, photo, or roll number pauses the holder's pass until faculty re-approval — detect it here, call gatepass-service to pause.

### 4.3 Gate Pass Service — `gatepassdb` (port 8083) — Tushar

**Entities:** `visitor_requests`, `gate_passes`, `events`, `bulk_upload_batches`.

**Requirements covered:** FR-APPR-1…6, FR-PASS-1…7, FR-BULK-1…10, FR-VIS-1…4.

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/gatepass/visitor-requests` | Visitor submits the registration form |
| GET | `/api/gatepass/visitor-requests/pending` | Approval queue for the caller's campus |
| PUT | `/api/gatepass/visitor-requests/{id}/approve` | Approve → creates pass, queues generation |
| PUT | `/api/gatepass/visitor-requests/{id}/reject` | Reject with a mandatory reason |
| GET | `/api/gatepass/passes/me` | Logged-in user's own passes |
| GET | `/api/gatepass/passes/{id}` | Fetch one pass |
| PUT | `/api/gatepass/passes/{id}/revoke` | Revoke with a mandatory reason |
| PUT | `/api/gatepass/passes/{id}/reissue` | Invalidates the old token, generates a new one |
| POST | `/api/internal/gatepass/passes/{id}/pause` | Internal — called by user-service on sensitive edit |
| POST | `/api/gatepass/passes/retrieve` | Visitor self-service — email + OTP, returns active passes |
| POST | `/api/gatepass/events` | Create an event (name, campus, date range) |
| DELETE | `/api/gatepass/events/{id}` | Cancel — revokes all its passes, notifies holders |
| GET | `/api/gatepass/events/{id}/attendance` | Registered / attended per day / never showed |
| GET | `/api/gatepass/bulk-upload/template` | Download the Excel template |
| POST | `/api/gatepass/bulk-upload` | Upload Excel (students or event visitors) |
| GET | `/api/gatepass/bulk-upload/{batchId}/status` | Poll validation and processing status |
| POST | `/api/gatepass/bulk-upload/{batchId}/retry-failed` | Retry only the failed rows |

**Screens built here:** 6 Visitor Registration, 7 Approval Queue, 8 My Pass, 12 Attendance Dashboard.

Screen 8 also serves visitor self-service: a holder enters their email, verifies an OTP, and sees their passes again. Non-active passes show status **without** the QR.

**Business rules.** One bulk engine for students and event visitors — only the `type` column and date source differ. Mixed batches resolved per row by email. Enforce the pass state machine, including `PAUSED`. Write a **scheduled job** that flips `ACTIVE → EXPIRED` daily; nothing does this today.

> Screens 9, 10, and 11 are built by Arham but call **these** APIs. Have the bulk and event endpoints ready by **Day 11** so he is unblocked in week 3.

### 4.4 Campus Service — `campusdb` (port 8084) — Arham

**Entities:** `campuses`, `campus_gates`, `campus_config`.

**Requirements covered:** FR-ADM-1…10, FR-CFG-1…5.

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/campus` | List campuses (Super Admin) |
| POST | `/api/campus` | Create a campus and its first Campus Admin |
| PUT | `/api/campus/{id}/suspend` | Suspend — data stays readable |
| PUT | `/api/campus/{id}/transfer-admin` | Move the Campus Admin role to another user |
| GET | `/api/campus/{id}/gates` | List gates |
| POST | `/api/campus/{id}/gates` | Add a gate |
| GET | `/api/campus/{id}/config` | Get campus settings |
| PUT | `/api/campus/{id}/config` | Update settings, validated and audited |
| POST | `/api/campus/{id}/staff` | Create a faculty or guard account |
| PUT | `/api/campus/staff/{userId}/deactivate` | Deactivate — never hard-delete |
| GET | `/api/campus/platform-stats` | Super Admin — figures across all campuses |

**Screens built here:** 16 Campus Admin, 19 Campus Settings, plus 9 Bulk Upload, 10 Bulk Progress, and 11 Event Management (which call gatepass-service and qr-service).

**Config keys to support:** `visitor_approval_required`, `repeat_entry_result`, `daily_pass_validity_days`, `max_visitor_duration_days`, `otp_expiry_minutes`, `photo_required_for_pass`.

> `repeat_entry_result` unblocks Palash's scanner. Ship it early.

**Business rules.** This service carries the campus-agnostic promise — if anything institution-specific is hardcoded elsewhere, the multi-tenant claim is false. Deactivating a user revokes their passes. A referenced department or gate cannot be deleted, only marked inactive.

### 4.5 Guard Service — `entrylogdb` (port 8085) — Palash

**Entities:** `entry_logs` (one MongoDB document per scan).

**Requirements covered:** FR-SCAN-1…11.

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/guard/session` | Bind the guard to one gate for this session |
| POST | `/api/guard/scan` | `{token, gateId}` → `{result, message, holder}` |
| GET | `/api/guard/logs` | Query by campus and date range |
| GET | `/api/guard/logs/pass/{passId}` | Scan history for one pass |

**Screens built here:** 13 Guard Scanner, 15 Guard Log, and **18 Audit Log** (which calls auth-service — structurally the same table as screen 15, which is why it sits here).

**Business rules.** Entry only, no exit scan. Behavior 2 auto-attribution lives here, not in the frontend. Amber comes from the campus config key `repeat_entry_result`, not a hardcoded rule. Cache active-pass lookups in Redis — the sub-second target is not reachable without it. Show the holder's name, photo, pass type, and validity on the result card. Camera unavailable, network down, and unreadable QR are **distinct** error states, not red denials.

### 4.6 QR Service — `qrdb` (port 8086) — Sanjay

**Entities:** `qr_records`, `generation_jobs`.

**Requirements covered:** FR-QR-1…5, FR-NOT-1…6.

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/qr/{passId}` | Object keys and validity for a pass |
| GET | `/api/qr/{passId}/download` | Stream the PDF |
| GET | `/api/qr/jobs/{jobId}/status` | Status of one async job |
| GET | `/api/qr/jobs/batch/{batchId}/progress` | Batch progress for the bulk screen |
| POST | `/api/internal/qr/decrypt` | Internal — called by Guard Service to decrypt a scanned token |
| POST | `/api/internal/qr/invalidate/{passId}` | Internal — called on re-issue, deactivates the old token |

**Screens built here:** 14 Pass Download, and **20 Super Admin Console** (which calls campus-service).

Screen 20 fills a real hole: Super Admin has been a role since SRS v1.0 but had no screen in the original 16.

**Business rules.** Consumes jobs from RabbitMQ dropped by Gate Pass Service. Generates an AES-256 token, a QR PNG, and a PDF; uploads both to object storage; updates the job; notifies Gate Pass Service to flip the pass to `ACTIVE`. Re-issue must deactivate the previous token immediately. Emails are retried a bounded number of times, then flagged. All campus-facing values in an email are substitution variables, never literals.

Notification is not a separate service. Pass, approval, and rejection emails go out from here; OTP and password-reset emails go out from auth-service.

> Arham's Bulk Progress screen (10) calls `/api/qr/jobs/batch/{batchId}/progress`. Have it ready by **Day 16**.

---

## 5. Non-Functional Requirements

- **API docs** — every service exposes Swagger. See Section 7.
- **Security** — no plaintext secrets anywhere, ever. See Section 2.4.
- **Async by default** — anything involving more than about ten QR/PDF generations or emails goes through RabbitMQ, never synchronously in a request.
- **Performance** — a gate scan returns in under one second, via Redis cache plus an async log write.
- **Consistency** — REST paths follow `/api/<service-noun>/<resource>` exactly as listed, so other teammates' pages can call them predictably. Internal-only endpoints live under `/api/internal/**` and are protected by the shared internal API key.
- **Operability** — every service exposes a health endpoint reporting its own status plus database and broker reachability. Logs are structured and carry a correlation id across service calls.

---

## 6. Frontend Ownership Model

There is no dedicated frontend-only teammate. Each member owns a vertical slice: their Spring Boot service, and the React pages that call it. Four screens are cross-assigned to balance load, since backend sizes differ a lot.

```
frontend/
└── src/
    ├── auth/       ← Omkar (1, 2) + Mukul (17) + Palash (18)
    ├── users/      ← Mukul
    ├── gatepass/   ← Tushar (6, 7, 8, 12) + Arham (9, 10, 11)
    ├── campus/     ← Arham (16, 19) + Sanjay (20)
    ├── guard/      ← Palash
    ├── qr/         ← Sanjay
    └── shared/     ← Omkar. Layout, routing, auth context. PR only.
```

### Full screen assignment

Screens 17–20 are new in v1.1.

| # | Screen | Built by | Calls |
|---|--------|----------|-------|
| 1 | Email Entry | Omkar | auth |
| 2 | OTP Verify | Omkar | auth |
| 3 | Student Profile | Mukul | user |
| 4 | Faculty Profile | Mukul | user |
| 5 | Student Directory | Mukul | user |
| 6 | Visitor Registration | Tushar | gatepass |
| 7 | Approval Queue | Tushar | gatepass |
| 8 | My Pass | Tushar | gatepass |
| 9 | Bulk Upload | Arham | gatepass |
| 10 | Bulk Progress | Arham | qr |
| 11 | Event Management | Arham | gatepass |
| 12 | Attendance Dashboard | Tushar | gatepass |
| 13 | Guard Scanner | Palash | guard |
| 14 | Pass Download | Sanjay | qr |
| 15 | Guard Log | Palash | guard |
| 16 | Campus Admin | Arham | campus |
| **17** | **Blocklist** | Mukul | auth |
| **18** | **Audit Log** | Palash | auth |
| **19** | **Campus Settings** | Arham | campus |
| **20** | **Super Admin Console** | Sanjay | campus |

Screen count: Omkar 2 plus the shared shell, Mukul 4, Tushar 4, Arham 5, Palash 3, Sanjay 2. Arham carries the most screens because campus-service is the lightest backend; Sanjay carries the fewest because qr-service is the heaviest.

Why this works: nobody waits on anyone else to see their own feature working end to end. Build and test the backend via Swagger first, then wire up the pages.

During early development, each person's pages call their own backend directly at its own port (for example `http://localhost:8081` for Auth). The API Gateway is added once services stabilise; paths do not change.

---

## 7. API Documentation — Swagger (required for every service)

Every Spring Boot service adds this to its `pom.xml`:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.6.0</version>
</dependency>
```

No extra configuration is required. Once the dependency is added and the service starts, Swagger UI is available at:

```
http://localhost:<service-port>/swagger-ui.html
```

and the raw OpenAPI spec at:

```
http://localhost:<service-port>/v3/api-docs
```

**Why this matters.** Swagger UI lets every teammate test and call any service's API in the browser, with a working "Try it out" button, before any frontend page exists. This is how you integrate without waiting on anyone's React work.

**Rule:** every `@RestController` method gets a short `@Operation(summary = "...")` annotation so the Swagger page is readable, not a list of raw paths.

---

## 8. Appendix — Where to find the rest

| Topic | Document |
|-------|----------|
| Formal requirements, FR codes, analysis models | `Perimity_SRS.pdf` |
| Corrections and additions to the SRS | `Perimity_SRS_v1.1_Amendments.md` |
| Full schema, column lists, object storage layout | `Perimity_Database_Design.md` |
| Identity vs pass model, bulk engine, gate scan decision tree, organiser dashboard | `Perimity_Event_Bulk_Design.md` |
| Day-by-day plan, demo script, viva guide | `Perimity-Complete-Roadmap.md` |
| Repo setup, branching, CI | root `README.md` and `.github/workflows/docker-build.yml` |
