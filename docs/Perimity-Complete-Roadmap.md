# Perimity — Complete Development Roadmap

**Smart Campus Access & Gate Pass Management System**
25-Day Sprint Plan · Database Design · APIs · UI Screens · Demo Script · Viva Guide

Version 1.1 · six-person team · one service each

| | |
|---|---|
| **Tech stack** | Spring Boot 3.x (Java 17) · React 18 · PostgreSQL 16 · MongoDB 7 · RabbitMQ · Redis · Docker · AWS (EC2 + S3) |
| **Timeline** | Days 1–5 Foundation · Days 6–12 Core Backend · Days 13–20 Frontend + Hardening · Days 21–25 Deploy + Demo |
| **Team size** | 6 members, 1 service each — every member owns backend **and** frontend for their service |
| **Core feature** | Async RabbitMQ QR/PDF pipeline + email-keyed identity resolution for mixed-attendee bulk onboarding |

**Perimity is campus-agnostic.** It is not built for any institution. Campus names, gates, and departments are data created at onboarding. Nothing institution-specific appears in code, seed data, or UI copy.

---

## Part 1 — What We Are Building

A paper gate register is slow to fill, impossible to search, easy to forge, and answers no questions afterwards. Perimity replaces it with QR gate passes that are time-bound, forgery-proof, and searchable — scaling from a single visitor to a 600-person event.

| Concern | Choice | Why |
|---------|--------|-----|
| Auth — visitors | Email + OTP, no password | A one-time visitor will abandon an account-creation form |
| Auth — staff | bcrypt password | Super Admin, Campus Admin, and Guard need durable credentials |
| Auth — members | Password **or** OTP, user's choice | Students and faculty get both |
| File storage | Object storage (S3 / MinIO) | Photos, ID documents, QR PNGs, PDF passes. DB stores only the key |
| Async work | RabbitMQ | 600 QRs + 600 PDFs + 600 emails cannot happen in a web request |
| Scan speed | Redis cache | Sub-second gate scan is a hard requirement |
| Scan storage | MongoDB | Append-only, join-free, millions of rows |
| Email | SMTP with an App Password, never a main password | |

---

## Part 2 — Architecture and Ownership

### 2.1 Microservices map

| Member | Service | Database | Type | Port | Frontend folder |
|--------|---------|----------|------|------|-----------------|
| Omkar | auth-service | `authdb` | PostgreSQL | 8081 | `frontend/src/auth/` |
| Mukul | user-service | `userdb` | PostgreSQL | 8082 | `frontend/src/users/` |
| Tushar | gatepass-service | `gatepassdb` | PostgreSQL | 8083 | `frontend/src/gatepass/` |
| Arham | campus-service | `campusdb` | PostgreSQL | 8084 | `frontend/src/campus/` |
| Palash | guard-service | `entrylogdb` | MongoDB | 8085 | `frontend/src/guard/` |
| Sanjay | qr-service | `qrdb` | PostgreSQL | 8086 | `frontend/src/qr/` |

All five PostgreSQL databases run on one Postgres container in development, created at startup by `docker/postgres/init-databases.sql`. `frontend/src/shared/` is touched by PR only.

### 2.2 Ownership model — vertical slices

Each member owns:

1. Their Spring Boot service (entities → repository → service → controller → Swagger)
2. The React pages that call it
3. Their own service's seed data and tests

Four screens are cross-assigned to balance load, because backend sizes differ a lot. See Part 5.

### 2.3 Rules that apply every single day

| Rule | Why |
|------|-----|
| No institution name, department list, or email domain in code | Perimity is campus-agnostic. Campus data comes from the API. CI fails the build if it finds one |
| No `Semester` field in any UI | Not needed for access control |
| Login differs by role — not passwordless for everyone | Password for Super Admin, Campus Admin, Guard. Password or OTP for Faculty and Student. OTP only for Visitor |
| Entry only — no exit scan, no in/out toggle | The register only ever recorded entry |
| Files to object storage, keys to DB | Never store binary in a database |
| bcrypt, SHA-256, AES-256 | No secret is ever stored in plain text |
| No service reads another service's database | Cross-service data comes from an API call, always |
| Nothing is hard-deleted | Deactivate accounts, suspend campuses, retain expired passes |
| Every endpoint gets a Swagger `@Operation` summary | An endpoint without Swagger docs is not "done" |

---

## Part 3 — Database Design

Database-per-service. Six databases, no shared tables. Full column lists live in `Perimity_Database_Design.md`; this is the working summary.

### 3.1 authdb — auth-service

**`users`** — `id`, `email` (unique, the universal key), `name`, `phone`, `password_hash` (bcrypt, NULL only for visitors), `role`, `campus_id` (NULL for Super Admin), `must_change_password`, `failed_login_count`, `locked_until`, `is_active`, `created_at`.

Roles: `SUPER_ADMIN`, `CAMPUS_ADMIN`, `FACULTY`, `STUDENT`, `VISITOR`, `GUARD`.

**`otp_verifications`** — `email` (indexed), `otp_hash` (SHA-256, never plain), `purpose`, `expires_at` (now + 10 min), `attempts` (locked at 5), `consumed`.

**`password_resets`** — single-use token hash, expiry, used flag.

**`blocklist`** — `campus_id`, `email`, `phone`, `reason` (mandatory), `created_by`, `created_at`.

**`audit_logs`** — `actor_user_id`, `actor_role`, `action`, `target_entity`, `campus_id`, `source_ip`, `created_at`. Append-only.

### 3.2 userdb — user-service

**`student_profiles`** — `user_id`, `roll_number`, `year`, `national_id_type`, `national_id_number`, `address`, `photo_key`, `status`, `approved_by`.

> No `semester` column — deliberate. Identity document columns are generic rather than country-specific, since Perimity is not bound to one jurisdiction.

**`faculty_profiles`** — `user_id`, `employee_id`, `designation`, `department_id`, `is_approver`.

**`departments`** — `campus_id`, `name`, `code`. **Ships empty.** Rows are created by each Campus Admin. Never seeded, never hardcoded in a dropdown.

**`documents`** — `object_key`, `doc_type` (`ID_DOCUMENT`, `CERTIFICATE`, `PHOTO`), `mime_type`, `verified`.

### 3.3 gatepassdb — gatepass-service

**`visitor_requests`** — name, email, phone, purpose, `host_id`, `visit_from`, `visit_to`, `otp_verified`, `status`, `reviewed_by`, `reject_reason`.

**`gate_passes`** — `holder_user_id`, `campus_id`, `visitor_request_id`, `pass_type` (`DAILY` / `EVENT`), `event_id`, `valid_from`, `valid_to` (NULL for standing daily), `status`, `revoked_reason`, `qr_key`, `pdf_key`.

**Pass states:** `PENDING` → `ACTIVE` → (`PAUSED` ⇄ `ACTIVE`) → `EXPIRED` or `REVOKED`. `PAUSED` is new in v1.1 and required when a sensitive profile field changes.

**`events`** — `campus_id`, `name`, `valid_from`, `valid_to`, `created_by`, `is_cancelled`.

**`bulk_upload_batches`** — `object_key`, `total_rows`, `valid_rows`, `invalid_rows`, `status`, `error_report_key`.

### 3.4 campusdb — campus-service

**`campuses`** — `name`, `code`, `address`, `logo_key`, `admin_user_id`, `is_suspended`.

**`campus_gates`** — `campus_id`, `name`, `location`.

**`campus_config`** — `campus_id`, `key`, `value`. Keys: `visitor_approval_required`, `repeat_entry_result` (`GREEN`/`AMBER`), `daily_pass_validity_days`, `max_visitor_duration_days`, `otp_expiry_minutes`, `photo_required_for_pass`.

### 3.5 entrylogdb — guard-service (MongoDB)

**`entry_logs`** — one document per scan: `pass_id`, `holder_user_id`, `holder_name`, `guard_id`, `gate_id`, `campus_id`, `event_id`, `result` (`GREEN`/`RED`/`AMBER`), `deny_reason`, `scanned_at`, `device`.

Indexes: compound `campus_id + scanned_at`; single on `pass_id`, `holder_user_id`, `event_id`.

Deny reasons: `EXPIRED`, `REVOKED`, `PAUSED`, `PENDING`, `INVALID_TOKEN`, `WRONG_CAMPUS`.

### 3.6 qrdb — qr-service

**`qr_records`** — `pass_id`, `token_hash`, `qr_key`, `pdf_key`, `valid_from`, `valid_to`, `is_active`.

**`generation_jobs`** — `pass_id`, `batch_id`, `status` (`QUEUED`/`PROCESSING`/`DONE`/`FAILED`), `retry_count`, `error_message`.

---

## Part 4 — API Surface by Service

Paths follow `/api/<service-noun>/<resource>`. Internal service-to-service endpoints live under `/api/internal/**` and are protected by a shared `X-Internal-Key` header, not JWT.

Full endpoint tables are in `Perimity_Team_Guide.md`, Section 4. The headline additions in v1.1:

| Service | New endpoints |
|---------|---------------|
| auth | `/login`, `/logout`, `/password/forgot`, `/password/reset`, `/password/change`, `/blocklist` (GET, POST, DELETE), `/audit-logs` |
| user | `/departments` (POST — Campus Admin creates them) |
| gatepass | `/passes/{id}/revoke`, `/passes/{id}/reissue`, `/passes/retrieve` (visitor self-service), `/bulk-upload/template`, `/bulk-upload/{id}/retry-failed`, `DELETE /events/{id}` |
| campus | `/campus/{id}/suspend`, `/transfer-admin`, `/staff`, `/staff/{id}/deactivate`, `/platform-stats` |
| guard | `/guard/session` (bind guard to one gate) |
| qr | `/jobs/batch/{batchId}/progress`, `/internal/qr/invalidate/{passId}` |

---

## Part 5 — Frontend Screens

Twenty screens. Screens 17–20 are new in v1.1.

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

Count: Omkar 2 + shared shell, Mukul 4, Tushar 4, Arham 5, Palash 3, Sanjay 2. Arham has the most screens because campus-service is the lightest backend; Sanjay has the fewest because qr-service is the heaviest.

---

## Part 6 — The Three Core Technical Pieces

These are what the viva will probe. Every member should be able to explain all three.

### 6.1 The async QR/PDF pipeline

Approval creates a `PENDING` pass and publishes a job to RabbitMQ, then returns immediately. The QR service consumes the job, generates an AES-256 token, renders a QR PNG, composes a PDF, uploads both to object storage, writes `qr_records`, calls gatepass-service to flip the pass to `ACTIVE`, and emails the pass. The approver never waits for any of it.

### 6.2 Mixed-attendee resolution — email as the universal key

A 600-row event sheet where roughly 100 attendees are already members. The faculty does not know which. Per row, matched by email: existing identity → reuse it, issue only an event pass; brand-new email → create a lightweight visitor identity plus an event pass. All 600 get a pass. Zero duplicate accounts.

Attendees from another campus already in the system are recognised the same way — the pass is scoped to the hosting campus's event, their identity is reused.

### 6.3 The two-QR problem and the gate scan decision tree

A student attending an event holds two valid QRs. If they scan the daily one out of habit, Behavior 2 auto-attributes the entry to the running event, so attendance stays accurate and the guard never has to decide.

```
Scan → decrypt token
  ├─ not authentic → RED (INVALID_TOKEN), logged
  └─ authentic
       ├─ not ACTIVE or out of date range → RED + reason, logged
       └─ valid
            ├─ already scanned today → result from campus config repeat_entry_result
            ├─ EVENT pass  → log against event → GREEN "Welcome to [Event]"
            └─ DAILY pass
                 ├─ event running for this person today → log against event → GREEN "Welcome to [Event]"
                 └─ otherwise → GREEN "Welcome"
```

---

## Part 7 — Day-by-Day Plan (25 Days)

**Daily ritual:** 15-minute standup, same time every day — what I finished, what I'm doing, what's blocking me.

### Week 1 — Foundation (Days 1–5)

#### Day 1 — Environment, repo, skeletons

- **Install** — JDK 17, Docker Desktop, Node.js 20, an IDE.
- **Repo** — clone. Confirm `docker compose up -d` starts postgres, mongo, rabbitmq, redis. Verify RabbitMQ UI at `localhost:15672`.
- **Verify the databases exist** — `docker exec -it perimity-postgres psql -U perimity -d postgres -c "\l"` must list `authdb`, `userdb`, `gatepassdb`, `campusdb`, `qrdb`. If not, `docker compose down -v` and start again.
- **Skeleton** — each owner generates a Spring Boot project via Spring Initializr into their folder. Dependencies: Web, JPA (Spring Data MongoDB for guard), Validation, Lombok, springdoc-openapi. Base package `com.perimity.<service>`.
- **Swagger** — add `springdoc-openapi-starter-webmvc-ui` 2.6.0. Confirm Swagger UI loads.
- **Ping** — each owner writes `GET /api/<service>/ping` returning `{"status":"ok"}` with an `@Operation` summary.
- **Branch** — push on `feature/<service>-setup`, open a PR, practise the review-and-merge flow once today.

**Deliverable:** all 6 skeletons boot, all 6 Swagger pages load, all 6 first PRs merged.

#### Day 2 — Data modelling (all services in parallel)

- **Entities** — write JPA entities or Mongo documents exactly matching Part 3. Add `@Column`, `@NotNull`, enums.
- **Config** — each service points at its own database in `application.yml`, reading credentials from env vars. `ddl-auto=update` for now.
- **Repositories** — one Spring Data interface per entity.
- **Verify** — boot each service, open a DB client, confirm tables and collections exist.
- **Checks** — user-service owner confirms there is no `semester` column and that `departments` is empty. Everyone greps their own code for institution names.

**Deliverable:** all 6 databases have real tables created from real entity code.

#### Day 3 — auth-service end to end (priority build)

- **OTP request** — `POST /api/auth/otp/request`: generate a 6-digit OTP, SHA-256 hash it, store with `expires_at = now + 10 min`, log the plain OTP to console (real email comes Day 5).
- **OTP verify** — hash the submission, compare, check expiry, check attempts < 5, mark consumed, issue JWT.
- **Password login** — `POST /api/auth/login` with bcrypt verification, for staff roles.
- **JWT** — `JwtUtil` generating claims `{userId, email, role, campusId}`, HMAC-SHA256, 24-hour expiry.
- **Redis** — attempt counters and lockout state in Redis with TTL, so brute-force protection survives restarts.
- **Audit** — write an `audit_logs` row on `LOGIN_SUCCESS`, `LOGIN_FAILED`, and `OTP_FAILED`.
- **Everyone else** — build one real Create and one real Read endpoint on your own service today, so you have something to protect tomorrow.

**Deliverable:** both OTP → JWT and password → JWT work via Swagger. Five wrong attempts locks the account.

#### Day 4 — Shared JWT validation across all six services

- **Shared filter** — auth-service owner writes `JwtAuthFilter` + `SecurityConfig` that validates signature and expiry locally, with no network call per request, and posts it in team chat as a copy-paste snippet.
- **Adopt** — all five other owners drop it in, set the same `JWT_SECRET` env var, protect at least one endpoint.
- **Internal key** — add the shared `X-Internal-Key` header check for `/api/internal/**`.
- **Role checks** — enforce `SUPER_ADMIN` vs `CAMPUS_ADMIN` separation now, while it is cheap.
- **CRUD** — gatepass, user, and campus owners finish first real Create and Read endpoints.

**Deliverable:** all 6 services enforce the same JWT. No token → 401, valid → 200, expired → 401.

#### Day 5 — Real email, session management, MILESTONE M1

- **SMTP** — auth-service owner adds `spring.mail.*` (port 587, STARTTLS, App Password).
- **Email service** — `EmailService.sendOtp(email, otp)` with a simple HTML template. Switch off console logging.
- **Sessions** — implement logout and the forgot-password flow (single-use, time-limited link) while you are in this code.
- **Secrets** — confirm every secret reads from `.env`. Only `.env.example` is committed.
- **Team sync (45 min)** — every member demos their Swagger page live.

**Deliverable — M1:** every service has real entities, at least 2 working endpoints, Swagger docs, and JWT protection. A real OTP email lands in a real inbox.

### Week 2 — Core Backend Pipeline (Days 6–12)

#### Day 6 — Visitor request flow, profiles, blocklist

- **gatepass** — `POST /visitor-requests` validating the date range and requiring OTP verification; `PUT /{id}/approve` creating a `gate_passes` row with `status = PENDING`, no QR yet.
- **user** — student and faculty CRUD, department list endpoint, `POST /documents` with a placeholder object key.
- **campus** — campus and gate CRUD. Seed **one neutral demo campus** with two gates. No institution name.
- **auth** — blocklist table plus add/list/remove endpoints; screen registration against it.
- **guard** — `entry_logs` document and repository with the indexes from Part 3.5.
- **qr** — `qr_records` and `generation_jobs` entities and repositories.

**Deliverable:** visitor request → approval → `PENDING` pass, provable in the database. A blocklisted email is rejected.

#### Day 7 — RabbitMQ wiring (producer + consumer handshake)

- **Config** — gatepass and qr both add `spring-boot-starter-amqp`. Declare queue `pass.generate` and dead-letter queue `pass.generate.dlq`.
- **Producer** — gatepass publishes `{passId, holderName, campusId, validFrom, validTo, batchId}` on approval.
- **Consumer** — qr `@RabbitListener` that today only logs the payload and writes a `generation_jobs` row with status `QUEUED`.

**Deliverable:** the message actually crosses from one service to another. This handshake is the foundation of Days 8–11.

#### Day 8 — QR service: token, QR, PDF, object storage (the big build day)

- **Token** — `TokenService.generate(passId, campusId, expiry)`: AES-256 encrypt, Base64 encode, plus a SHA-256 `token_hash` for lookup.
- **QR** — `QRCodeService.generate(token)`: 300×300 PNG.
- **PDF** — `PdfService.generate(pass, qrBytes)`: QR image, holder name, campus name **from data**, validity dates.
- **Storage** — `StorageService.upload(key, bytes, contentType)`. If the AWS account is not ready, use LocalStack or MinIO today. Do not let AWS signup block this day.
- **Wire it** — inside the listener: token → QR → PDF → upload both → save `qr_records` → job `DONE` → call gatepass's internal activate endpoint.
- **gatepass** — implement `POST /api/internal/gatepass/passes/{id}/activate`: store both keys, set `status = ACTIVE`.

**Deliverable:** the full async pipeline works end to end, with no frontend involved.

#### Day 9 — Guard service: scan logic, and pass lifecycle

- **qr internal** — `POST /api/internal/qr/decrypt`: decrypt, look up by `token_hash`, return holder and validity or 404.
- **guard scan** — `POST /api/guard/scan`: decrypt via qr-service, check active status and date range, return GREEN with holder name or RED with a specific `deny_reason`.
- **Redis** — cache the active-pass lookup here. The sub-second target is not reachable without it.
- **Log both outcomes** — denied attempts are security data, never discard them.
- **gatepass** — implement revoke (with mandatory reason) and the `PAUSED` transition, so there are real RED cases to test.

**Deliverable:** the core gate scan works. This is the heart of the product.

#### Day 10 — Events, MILESTONE M2

- **gatepass** — `events` table, create and list endpoints, `pass_type` and `event_id` handling in pass creation. Approving a request tied to an event produces an `EVENT` pass with the event's dates.
- **guard** — an `EVENT` pass logs `event_id` and returns GREEN "Welcome to [Event]".
- **Team sync** — demo the entire backend pipeline service by service through Swagger.

**Deliverable — M2:** request → approve → RabbitMQ → QR + PDF stored → `ACTIVE` → scan → GREEN, with an event pass variant. Fully provable without any UI.

#### Day 11 — The bulk engine

- **Parse** — Apache POI reads the `.xlsx`. Minimum columns for an event batch: `name, email, phone, purpose`. Dates come from the event, not per row.
- **Validate** — per row: valid email? duplicate in this sheet? already has a pass for this event? on the campus blocklist? Collect errors as `row 34: invalid email`.
- **Summary** — return `{batchId, totalRows, validRows, invalidRows}` fast (~2 seconds), status `AWAITING_CONFIRM`, error report written to object storage.
- **Resolve** — on confirm, for each valid row call auth's internal by-email lookup. Exists → reuse. New → create a lightweight `VISITOR` identity.
- **Publish** — create N pass rows, publish N jobs, set batch `PROCESSING`, return immediately.
- **Template** — ship the downloadable Excel template and enforce the row limit today, not later.

**Test:** a 10-row sheet with 3 existing emails, 5 new, 1 duplicate, 1 malformed → expect 8 valid / 2 errors, 5 new identities, 8 passes, zero duplicate accounts.

**Deliverable:** mixed-attendee resolution provably correct on a real Excel file.

#### Day 12 — Behavior 2, amber, campus config, attendance

- **campus** — `campus_config` key-value store with the six v1.1 keys and validated writes. **Ship `repeat_entry_result` first** — guard-service is blocked on it.
- **gatepass internal** — `GET /api/internal/gatepass/passes/{userId}/active-event` returns the event id if that person has an event running today, else null.
- **guard — Behavior 2** — if the scanned pass is `DAILY`, call that endpoint; if an event is running, log with that `event_id` and return GREEN "Welcome to [Event]".
- **guard — amber** — a repeat entry on the same day returns the result named by `repeat_entry_result`. Log it either way. Camera, network, and unreadable-QR failures are separate error states, not RED.
- **Attendance** — guard aggregates distinct holders per day from MongoDB; gatepass combines it with the registered count.
- **gatepass** — the daily scheduled job that flips `ACTIVE → EXPIRED`.

**Deliverable:** the two-QR problem is solved and demonstrable. Attendance numbers are correct.

### Week 3 — Frontend (Days 13–17)

#### Day 13 — React setup + auth screens (whole-team kickoff)

- **Setup** — `npm create vite@latest frontend -- --template react`. Install axios, react-router-dom, tailwindcss. One person does this and pushes it.
- **Shared shell (30-min team huddle)** — agree `App.jsx` routes, `AuthContext.jsx`, `ProtectedRoute.jsx`, Navbar, Sidebar, Toast. Omkar implements; everyone reviews the PR together.
- **Axios** — `api/client.js` with an interceptor attaching `Authorization: Bearer <jwt>` and redirecting to login on 401.
- **Screens 1 and 2** — email entry, then either a password field or a six-box OTP screen with countdown, resend, and attempts-left warning. **The login page must offer both paths**: password for all staff roles, "log in with OTP instead" for Faculty and Student, OTP-only for Visitor.
- **Folders** — every other owner creates their folder with one placeholder page wired into routing.

**Deliverable:** real login works in the browser, by both password and OTP. Every member has a routed page to build into.

#### Day 14 — Core screens, part 1

- **Mukul (3, 4, 5)** — student profile view/edit with no semester field, faculty profile, student directory with department filter chips and pagination. Department chips come from the API.
- **Tushar (6, 7)** — public visitor registration with the OTP step inline; approval queue with Approve/Reject and a reject-reason modal.
- **Arham (16)** — campus list/edit, gate management table.
- **Pattern** — every screen handles three states: loading, error, empty. Agree the look once and reuse.

**Deliverable:** half the UI is real and calling real APIs.

#### Day 15 — Core screens, part 2, MILESTONE M3

- **Palash (13)** — scanner: camera QR capture with a manual-token fallback. Full-screen GREEN / RED / AMBER card showing holder name, photo, pass type, and validity; auto-reset after 5 seconds. The colour must fill the whole screen and be readable at arm's length on a tablet. Distinct error states for camera and network failure.
- **Sanjay (14)** — pass download: large QR image, validity dates, Download PDF button.
- **Tushar (8)** — My Pass, correctly rendering two passes side by side when a student holds both `DAILY` and `EVENT`. Also the email + OTP retrieval path for visitors who lost their pass.
- **Team sync — M3** — one complete journey through the real UI.

**Deliverable — M3:** a full user journey works through real screens, not Swagger.

#### Day 16 — Bulk, attendance, and admin screens (the demo centrepiece)

- **Arham (9)** — bulk upload: drag-drop Excel, type selector, event picker, then the "580 valid, 20 errors" summary with an error-report download and a Confirm button. Template download link.
- **Arham (10)** — bulk progress: poll `/jobs/batch/{batchId}/progress` every 2 seconds, show "412 of 580 generated", offer retry-failed-rows.
- **Arham (11, 19)** — event create form with cancel; campus settings page for the six config keys.
- **Tushar (12)** — attendance dashboard: registered / attended per day / never showed cards, a per-day bar chart, attendee search, Export CSV.
- **Palash (15, 18)** — guard log table with date and result filters and colour-coded badges; audit log table, same pattern.
- **Mukul (17)** — blocklist screen: add with mandatory reason, search, remove.
- **Sanjay (20)** — Super Admin console: create and suspend campuses, create Campus Admins, platform statistics.

**Deliverable:** the strongest demo screens are working, and all 20 screens exist.

#### Day 17 — Welcome email and notifications

- **Queue** — add a `notification.send` queue. qr-service publishes to it after a pass goes `ACTIVE`.
- **Email** — the consumer builds the welcome email: subject `Your gate pass for {event_name} at {campus_name}`, body with name, dates, and gate, PDF attached. **Every campus-facing value is a substitution variable, never a literal.** One email, not two.
- **Bulk email** — confirm 50 emails send without blocking and that one bad address does not stop the rest. Bounded retry, then flag.

**Deliverable:** the real-world loop closes — register, receive pass by email, walk to gate, scan, enter.

### Week 4 — Hardening (Days 18–20)

#### Day 18 — Error handling and validation everywhere

- **Global handler** — every service adds `@RestControllerAdvice` returning `{success, message, data, errors}`. Map exceptions properly: 400 validation, 401 auth, 403 role, 404 missing, 409 conflict. Never leak a stack trace.
- **Validation** — `@Valid`, `@NotBlank`, `@Email`, `@Future` on every request DTO.
- **Health endpoints** — each service reports its own status plus database and broker reachability.
- **Frontend** — field-level errors on every form, a toast on every API failure, never a blank screen.
- **Edge cases** — a 50-row Excel with deliberately broken rows; amber paths; scanning a revoked pass; scanning a paused pass; an OTP after 11 minutes; a fourth OTP request inside 15 minutes.

**Deliverable:** no unhandled 500 anywhere on expected bad input.

#### Day 19 — Cross-team regression testing

**Pair up and test someone else's service, never your own.** Rotate 1↔4, 2↔5, 3↔6. Bugs found by the author are the ones that were never going to be found.

1. Single visitor — register → OTP → approve → email → scan → GREEN. Scan again → the result matches `repeat_entry_result`, and the entry is still logged.
2. Bulk student import — 20 rows → all `DAILY` passes, no end date.
3. Mixed event batch — existing members reused, new visitors created, zero duplicates.
4. Behavior 2 — student scans daily QR during their event → attendance still credited.
5. All three colours — GREEN, RED for each deny reason, AMBER.
6. RBAC — a `STUDENT` token calling an admin endpoint → 403 everywhere. A `CAMPUS_ADMIN` of campus A cannot see campus B's data.
7. Pass lifecycle — edit a sensitive profile field → pass goes `PAUSED` → re-approve → `ACTIVE`. Revoke → RED. Blocklist someone → their pass is revoked.
8. Campus-agnostic check — grep the whole repo for any institution name. CI should already catch this.

Every bug becomes a GitHub Issue with a severity label. P1 breaks the demo; P2 is cosmetic.

**Deliverable:** every bug has an issue number, an owner, and a severity.

#### Day 20 — Bug fix day, MILESTONE M4

- **Fix** all P1 issues. P2 only if P1 is clear.
- **Indexes** — PostgreSQL indexes on `gate_passes(holder_user_id)`, `gate_passes(status)`, `visitor_requests(status)`, `otp_verifications(email)`, `audit_logs(campus_id, created_at)`. Verify the MongoDB compound index is used with `.explain()`.
- **Review** — walk all 20 screens manually. Broken layouts, missing empty states, raw JSON errors.

**Deliverable — M4:** every core journey works with zero manual workarounds.

### Week 5 — Deploy and Demo (Days 21–25)

#### Day 21 — Seed data and Dockerfiles

- **Dockerfiles** — multi-stage per service: `maven:3.9-eclipse-temurin-17` to build, `eclipse-temurin:17-jre` to run. Frontend: `node:20` build, `nginx:alpine` serve.
- **Compose** — uncomment all 6 service blocks. The health checks and `depends_on` conditions are already written.
- **Seed** — a `DataSeeder` per service that runs only when the database is empty. Seed **one neutral demo campus** ("Demo Campus") with two gates, a small invented department list created through the normal admin flow, `superadmin@example.com`, `admin@example.com`, `guard@example.com`, around 15 students with daily passes, and one event with roughly 30 attendees plus a few days of realistic scan history so the attendance dashboard is not empty.

> **No institution name and no real-looking institutional email domain in seed data.** Demonstrating the onboarding flow live is a stronger showing than a pre-baked campus, because it proves the multi-tenant claim rather than asserting it.

**Test:** `docker compose down -v && docker compose up --build` from scratch. All containers healthy. Log in and see seeded data.

**Deliverable:** one command starts the whole system, fully seeded and demo-ready.

#### Day 22 — Cloud deployment

- **EC2** — a t3.medium Ubuntu instance; t2.micro is too small for ten containers. Security group: 22 (SSH, your IP only), 80, 3000. Do not expose database ports publicly.
- **Install** — Docker and Docker Compose on the instance.
- **Deploy** — clone the repo, create the real `.env` on the server by hand. Secrets live on the server, never in Git. `docker compose up -d --build`.
- **Gotchas, budget real time for exactly these two** — the frontend's API base URL must point at the public IP, not localhost; and CORS on each service must allow that origin.

**Deliverable:** the system is live and reachable from any browser.

#### Day 23 — Real object storage, live smoke test, MILESTONE M5

- **S3** — create the real bucket. Create an IAM user with bucket-scoped permissions only. Credentials into the server `.env`. Switch off LocalStack.
- **CORS** — configure bucket CORS so pre-signed browser uploads work.
- **Smoke test on the deployed system, not localhost** — register a visitor → approve → email arrives → open the PDF on a phone → scan that phone screen at the guard screen on a laptop → GREEN.
- **Bulk on prod** — upload a 50-row sheet on the live system and confirm all passes generate.

**Deliverable — M5:** the complete demo journey works against the live deployment with real object storage.

#### Day 24 — Documentation and slides

- **README** — prerequisites, clone and run commands, `.env.example` explanation, seeded demo credentials, service/port table, architecture diagram.
- **Diagram** — one clean architecture diagram: 6 services, 2 database types, RabbitMQ, Redis, object storage, React. Everyone must be able to point at their own box and explain it.
- **Slides** — problem → architecture → the three core technical pieces (Part 6) → live demo → learnings. Under 12 slides.
- **Assign** — each owner presents their own service in the architecture slide.

**Deliverable:** README and slides complete and committed.

#### Day 25 — Dress rehearsal, code freeze, MILESTONE M6

- **Rehearse** — run the Part 9 demo script end to end, timed, three times. Every member knows their cue.
- **Contingency** — record a 3-minute screen capture of the working demo as backup if the venue network fails. Keep the seeded local Docker setup as a second fallback.
- **Viva drill** — each member answers 3 questions about their own service, unprompted, without notes.
- **Freeze** — no new features. Critical bug fixes only, with a second person reviewing.

**Deliverable — M6:** demo rehearsed 3+ times. Every member can explain their service in 60 seconds. Backup video recorded.

### Days 26–30 — Buffer (do not schedule features here)

Reserve this for slippage. Days 18–20 are statistically where student projects overrun. If genuinely unused, in priority order: (1) domain name and HTTPS, (2) load-test the real 600-row bulk scenario, (3) UI polish, (4) prepare the API Gateway or scaling story as a talking point rather than something to build.

---

## Part 8 — Project Folder Structure

```
perimity/
├── docker-compose.yml
├── .env.example
├── docker/postgres/init-databases.sql
├── auth-service/
│   └── src/main/java/com/perimity/auth/
│       ├── controller/  (AuthController, BlocklistController, AuditController)
│       ├── service/     (OtpService, JwtService, BlocklistService, AuditService)
│       ├── repository/  · entity/  · config/  · security/
├── user-service/
│   └── src/main/java/com/perimity/user/
├── gatepass-service/
│   └── src/main/java/com/perimity/gatepass/
│       ├── controller/  · service/  · repository/  · entity/
│       ├── messaging/   (PassGenerationProducer)
│       └── scheduler/   (PassExpiryJob)
├── campus-service/
│   └── src/main/java/com/perimity/campus/
├── guard-service/
│   └── src/main/java/com/perimity/guard/
├── qr-service/
│   └── src/main/java/com/perimity/qr/
│       ├── service/     (TokenService, QRCodeService, PdfService, StorageService)
│       ├── messaging/   (PassGenerationConsumer, NotificationProducer)
│       └── config/      (RabbitConfig, StorageConfig)
└── frontend/
    └── src/
        ├── api/       (client.js, authApi.js, userApi.js, gatepassApi.js, guardApi.js, qrApi.js, campusApi.js)
        ├── shared/    (Navbar, Sidebar, ProtectedRoute, Toast, LoadingSpinner, EmptyState, AuthContext)
        ├── auth/      (EmailEntry, OtpVerify, PasswordLogin, ForgotPassword, Blocklist, AuditLog)
        ├── users/     (StudentProfile, FacultyProfile, StudentDirectory)
        ├── gatepass/  (VisitorRegistration, ApprovalQueue, MyPass, BulkUpload, BulkProgress, EventManagement, AttendanceDashboard)
        ├── campus/    (CampusAdmin, GateManagement, CampusSettings, SuperAdminConsole)
        ├── guard/     (GuardScanner, GuardLog)
        ├── qr/        (PassDownload)
        └── utils/     (jwtUtils.js, dateUtils.js, constants.js)
```

**Java base package is `com.perimity.*`** — neutral, matching the campus-agnostic product.

---

## Part 9 — Milestones

| Milestone | Day | What must be true | How to verify |
|-----------|-----|-------------------|---------------|
| **M1** | 5 | All 6 services boot with real entities, Swagger, and JWT protection. Real OTP email delivers. Password login works | Open all 6 Swagger pages. Request an OTP, receive a real email, verify, get a JWT. Log in with a password |
| **M2** | 10 | Full backend pipeline: request → approve → RabbitMQ → QR + PDF stored → `ACTIVE` → scan → GREEN | Swagger only, no UI. Check storage for both files. Check MongoDB for the entry log |
| **M3** | 15 | One complete user journey works through real UI screens | Browser: register → approve → scan → green card fills the screen |
| **M4** | 20 | All 8 test scenarios pass. All P1 bugs closed | Re-run the Day 19 scenario list. Zero manual workarounds |
| **M5** | 23 | Live on EC2 with real object storage. Demo journey works against the deployed system | Open the public IP on a phone. Complete the journey there |
| **M6** | 25 | Demo rehearsed 3×. Every member explains their service in 60 seconds. Backup video exists | Mock viva with no notes. Timed rehearsal completes without failure |

---

## Part 10 — Demo Flow Script (10 minutes)

Assign one member per section. Rehearse three times on Day 25.

**1. Intro (30 sec)** — "Most campus gates still run on a paper register. A visitor writes their name, a guard squints at it, and nobody can ever search it again. Perimity replaces that register with QR passes that are time-bound, forgery-proof, and searchable — and it scales from one visitor to a 600-person event. It is campus-agnostic: one deployment serves any number of institutions."

**2. Visitor registers (1.5 min)** — Open the public registration form. Fill in a visitor, submit. OTP arrives in the inbox on screen; enter it. "No password for a visitor, ever. A one-time guest never creates an account — email is the only identity they need."

**3. Admin approves (1 min)** — Log in as the campus admin, with a password. Open the approval queue, approve. "That response came back instantly, because nothing slow happened yet. Pass generation was pushed onto a RabbitMQ queue."

**4. The email arrives (1 min)** — Switch to the inbox. A welcome email with the PDF attached. Open it: QR, name, validity dates. "Generated in the background: token encrypted, QR rendered, PDF composed, uploaded to object storage, emailed. The admin never waited for any of it."

**5. Guard scans — GREEN (1 min)** — Guard scanner on a second screen. Scan the QR straight off the phone. Full-screen green, visitor name and photo. "One scan, one colour. The guard makes no decisions."

**6. Guard scans — RED (45 sec)** — Scan a revoked or expired pass. Full-screen red with the reason. "Denied attempts are logged too — that's security data a paper register throws away."

**7. The bulk story (2.5 min)** — "Now the part a paper register could never survive." Open bulk upload, drop in a 600-row sheet. "580 valid, 20 errors", with a downloadable error report. Confirm. Progress bar moves. "About a hundred of those 600 are already members here. The faculty doesn't know which ones — and doesn't need to. The engine matches every row by email: existing people get an event pass on their existing identity, brand-new people get a lightweight visitor identity. Zero duplicates."

**8. Behavior 2 (1 min)** — A student holding both a daily and an event pass scans their daily QR. Green — "Welcome to [Event]". "They scanned the wrong QR out of habit. The system attributed the entry to the event anyway, so the organiser's attendance stays accurate. The guard never had to know."

**9. The payoff (1 min)** — Attendance dashboard: registered 600, attended day 1 543, day 2 478, never showed 41. Export CSV. "This is the strongest argument for the whole system. No register in the world produces this."

**10. Architecture (45 sec)** — Show the diagram. Each member names their service in one sentence. Point at RabbitMQ: "This is why the 600-row upload didn't time out." Point at MongoDB: "This is why millions of scans stay fast."

---

## Part 11 — Viva Preparation

Every member answers questions about their own service, plus these architecture questions.

**Q: Why microservices and not a monolith for a student project?**

- Team ownership: six people, six services — each owns a service end to end and can build, run, and deploy without waiting on anyone.
- Independent scaling: the guard scan endpoint is high-frequency on event mornings; it can scale without scaling profile management.
- Failure isolation: if QR generation crashes mid-batch, visitors can still register and guards can still scan existing passes.
- Honest caveat: for a single-campus deployment a monolith would be simpler. We chose microservices deliberately for the ownership model and to demonstrate distributed-system competence — and we can defend where it cost us: cross-service calls, no cross-database joins.

**Q: Why no Eureka or Spring Cloud service discovery?**

- Eureka solves dynamic discovery — many instances per service, appearing and disappearing, needing load balancing.
- We run exactly one instance per service under Docker Compose, and Docker's DNS already resolves `http://qr-service:8080` by container name.
- Adding a registry means one more service to run, configure, monitor, and have fail — with zero benefit at this scale.
- The moment we scale to multiple instances or move to Kubernetes, discovery becomes necessary. Not adding it now is a scoping decision, not an oversight.

**Q: Why RabbitMQ? Why not call the QR service directly over HTTP?**

- Time: 600 passes means 600 token generations, 600 QR renders, 600 PDFs, 600 uploads, 600 emails. Synchronously that is minutes; the browser times out.
- Decoupling: the gate pass service should not care whether the QR service is even running. It publishes and returns.
- Resilience: if the QR service is down, jobs queue and process on restart. With a direct HTTP call the approval would simply fail.
- Independent failure: one bad row fails one job; the other 579 are unaffected. A synchronous loop would abort the whole batch.

**Q: Why two databases — PostgreSQL and MongoDB?**

- Gate passes and approvals need transactions, foreign keys, and consistency. That is PostgreSQL's job.
- Gate scans are append-only, join-free, self-contained events arriving at high volume — millions of rows over years. That is MongoDB's job.
- The scan document needs no relationships: pass id, guard, gate, timestamp, result. Forcing it into a relational schema adds JOIN cost for no gain.
- We index MongoDB on `campus_id + scanned_at` as a compound index, which covers nearly every attendance and log query in a single index scan.

**Q: Why is login different for different roles?**

- The primary user is a one-time visitor. Forcing account creation for a single gate entry would kill adoption — most people would abandon the form. So visitors are OTP-only: a 10-minute, single-use, SHA-256-hashed code, with no password to leak, reuse, forget, or phish.
- Staff are different. A Campus Admin logs in daily and manages other people's access; a Guard logs in at shift start on a shared device. Depending on email delivery for those logins would be fragile and slow. They use bcrypt passwords with lockout after repeated failures and a forgot-password flow.
- Students and faculty get both, and choose. They are frequent enough to want a password but casual enough to have forgotten it.
- Honest limitation: OTP security depends on the user's email account being secure, and email delivery adds latency. In production we would add per-IP rate limiting and consider TOTP for staff.

**Q: What are the roles and what can each do?**

- **Super Admin** — creates and suspends campuses, creates Campus Admins, sees platform-wide statistics. Spans all campuses; the only role with no `campus_id`.
- **Campus Admin** — one campus: faculty and guard accounts, gates, departments, policy config, blocklist, approvals, audit log.
- **Faculty** — approves student registrations and visitor requests, runs bulk uploads, creates events.
- **Student** — own profile, own passes. A standing daily pass, plus event passes.
- **Visitor** — register a visit, verify by OTP, view and download their own pass. Nothing else.
- **Guard** — the scan endpoint and their own gate's log. Cannot create passes, cannot see profiles.

Everything is scoped by `campus_id`, so a Campus Admin of one campus cannot see another's data.

**Q: How does the system handle a person who is both a student and an event attendee?**

- We separate identity from pass. One identity per person, keyed by email, forever. A pass is permission to enter for a purpose and a time window.
- That person holds two active passes at once: a daily pass with no end date, and an event pass valid only for the event dates. Like a company ID badge plus a conference lanyard.
- The bulk engine matches by email, recognises them as existing, and issues only the event pass — never a duplicate account.
- At the gate, if they scan the daily QR during the event, Behavior 2 auto-attributes the entry to the running event so attendance stays correct.

**Q: Explain the bulk upload flow and why it is split into two phases.**

- Phase 1, fast and synchronous, about 2 seconds: parse the Excel, validate every row, check duplicates and blocklist, return "580 valid, 20 errors" plus a downloadable error report. The faculty is still watching the screen.
- Phase 2, slow and asynchronous: after Confirm, create identities, publish one job per pass, return immediately. The faculty can close the laptop.
- The split exists because validation must be interactive — the faculty needs to see errors and decide — but generation must not be. Mixing them would either time out or hide errors until it was too late to fix them.
- Bad rows never block the batch, and the faculty can retry only the flagged rows.

**Q: What happens if the QR service crashes halfway through a 600-pass batch?**

- Unprocessed jobs stay in the queue — not lost, because we acknowledge a message only after the job completes.
- On restart the listener resumes where it stopped.
- Passes already generated are `ACTIVE`. Passes not yet generated remain `PENDING` with a `QUEUED` job row, so the state is always recoverable and auditable.
- Individually failing jobs retry a bounded number of times, then move to a dead-letter queue and are marked `FAILED` with the error, for manual escalation.

**Q: How is the QR code secure? Can someone forge one?**

- The QR encodes no pass ID and no name — it encodes an AES-256 encrypted token containing `pass_id`, `campus_id`, and expiry.
- Without the server-side key, an attacker cannot construct a token that decrypts to anything valid.
- Even a correctly decrypted token is then checked against the database: does this token hash exist, is `is_active` true, is today within the validity window?
- A screenshot of someone else's valid QR would work — a deliberate, documented tradeoff, exactly as a paper pass could be handed over. We mitigate by displaying the holder's photo on the guard's green screen so the guard can match face to pass.

**Q: Why entry-only? Why no exit scan?**

- The stated goal is to replace the paper register, and that register only ever recorded entry.
- Exit scanning doubles gate friction and halves throughput at peak times — a real cost for a marginal data gain.
- Repeat entries are all logged as separate rows, exactly as a register would have multiple lines for the same person.
- For a multi-day event, each day's first scan is that day's attendance, which is what an organiser actually needs.

**Q: What makes this campus-agnostic rather than built for one institution?**

- The system ships with no campus, no department list, and no email domain. A Super Admin creates a campus; that campus's admin creates its gates and departments through the UI.
- Every campus-facing string in an email or page is a substitution variable resolved from the `campuses` table, never a literal.
- Every campus-specific row carries a `campus_id`, and access control is scoped by it.
- CI fails the build if an institution name appears anywhere in the source. Hardcoding one institution's course list would have made the multi-tenant claim false.

**Q: What are the known limitations?**

- Single instance per service — no high availability. If a container dies, that capability is down until it restarts.
- A QR screenshot can be shared; we mitigate by showing the holder's name and photo on the green screen, not by preventing it technically.
- Cross-service data requires an API call, so some screens make two round trips where a monolith would use one JOIN.
- The API Gateway is specified but thin — JWT validation and routing only, with no rate limiting or circuit breaking yet.
- Scan performance depends on Redis. If the cache is cold, the first scan of a pass is slower than the sub-second target.

Stating these clearly is deliberate. Every architecture has tradeoffs, and knowing yours is the point.

---

## Appendix — Rules Recap (print this and stick it on the wall)

1. **No institution name, department list, or email domain in code.** Campus data comes from the API.
2. **No `Semester` field in any UI. Ever.**
3. **Login differs by role.** Password for Super Admin, Campus Admin, Guard. Password or OTP for Faculty and Student. OTP only for Visitor.
4. **Entry only.** No exit scan, no in/out toggle.
5. **Files go to object storage; the database stores only the key.**
6. **bcrypt for passwords, SHA-256 for OTPs, AES-256 for QR tokens.** Nothing in plain text. No personal data in a QR.
7. **No service reads another service's database.** API calls only.
8. **Nothing is hard-deleted.** Deactivate, suspend, retain.
9. **Every endpoint gets a Swagger `@Operation` summary** before it counts as done.
10. **Never push to `main`.** Branch → PR → 1 review → merge.
11. **Real secrets live in `.env`, never committed.** Only `.env.example` is.
