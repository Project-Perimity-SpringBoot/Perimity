# Perimity — Database Design Reference

**Smart Campus Access & Gate Pass Management System**
Six microservices · Spring Boot · PostgreSQL + MongoDB · Docker · AWS

Version 1.1 · aligned with `Perimity_SRS.pdf` and `Perimity_SRS_v1.1_Amendments.md`

---

## Overview

Perimity uses a **database-per-service** pattern. Each microservice owns its own database — no shared database between services. Transactional data lives in PostgreSQL; high-volume append-only logs live in MongoDB.

| Service | Database | Type | Service port | DB port |
|---------|----------|------|--------------|---------|
| Auth Service | `authdb` | PostgreSQL | 8081 | 5432 |
| User Service | `userdb` | PostgreSQL | 8082 | 5432 |
| Gate Pass Service | `gatepassdb` | PostgreSQL | 8083 | 5432 |
| Campus Service | `campusdb` | PostgreSQL | 8084 | 5432 |
| Guard Service | `entrylogdb` | MongoDB | 8085 | 27017 |
| QR Service | `qrdb` | PostgreSQL | 8086 | 5432 |

**Note on ports.** All five PostgreSQL databases run on a *single* Postgres container (port 5432) in development, to keep memory use low on one machine. They are created at container startup by `docker/postgres/init-databases.sql`. The database-per-service boundary is enforced **in code**: each service connects only to its own database name and holds no credentials for any other. Splitting into five containers later requires no code change, only a compose change.

---

## Campus-agnostic rule

Perimity ships with **no** campus, **no** department list, and **no** email domain. Every institution-specific value is data created through the application at onboarding time.

- Do not seed department names in any migration or `DataSeeder`.
- Do not write a campus name into a code literal, an email template, or a test fixture.
- Seed at most one neutral demo campus (for example "Demo Campus") for the presentation.

---

## AuthDB (PostgreSQL)

Owns authentication, users, blocklist, and the security audit trail.

| Table | What it stores | Why |
|-------|---------------|-----|
| `users` | Email, password hash, role, campus_id, lockout state | Every person who logs in — one record per user across all campuses |
| `otp_verifications` | SHA-256 hashed OTP, expiry, attempts | Email verification for visitors, and OTP login for students and faculty |
| `password_resets` | Single-use token hash, expiry, used flag | Forgot-password flow (FR-SESS-5) |
| `blocklist` | Banned email or phone, reason, campus_id | Screened at registration and during bulk upload (FR-BLK) |
| `audit_logs` | Who did what, when, from where | Security trail — logins, approvals, revocations, config changes |

### `users`

| Column | Type | Constraint | Notes |
|--------|------|-----------|-------|
| `id` | BIGSERIAL | PK | |
| `email` | VARCHAR(150) | UNIQUE, NOT NULL | The universal key across the whole system |
| `name` | VARCHAR(100) | NOT NULL | |
| `phone` | VARCHAR(20) | NULLABLE | |
| `password_hash` | VARCHAR(255) | NULLABLE | bcrypt. NULL only for visitors, who never have a password |
| `role` | VARCHAR(20) | NOT NULL | `SUPER_ADMIN`, `CAMPUS_ADMIN`, `FACULTY`, `STUDENT`, `VISITOR`, `GUARD` |
| `campus_id` | BIGINT | NULLABLE | Multi-tenant scope. NULL for Super Admin, who spans all campuses |
| `must_change_password` | BOOLEAN | DEFAULT FALSE | TRUE for accounts created by an administrator (FR-SESS-4) |
| `failed_login_count` | INT | DEFAULT 0 | Reset on success |
| `locked_until` | TIMESTAMP | NULLABLE | Set after too many failures (FR-SESS-7) |
| `is_active` | BOOLEAN | DEFAULT TRUE | Deactivate, never hard-delete (FR-ADM-6) |
| `created_at` | TIMESTAMP | DEFAULT NOW() | |

**Login by role.** This is not the same for everyone, and an earlier draft that said "passwordless everywhere" was wrong.

| Role | Password | OTP |
|------|----------|-----|
| Super Admin | yes | no |
| Campus Admin | yes | no |
| Guard | yes | no |
| Faculty | yes | yes — user chooses |
| Student | yes | yes — user chooses |
| Visitor | never | yes, only |

### `otp_verifications`

| Column | Type | Constraint | Notes |
|--------|------|-----------|-------|
| `id` | BIGSERIAL | PK | |
| `email` | VARCHAR(150) | NOT NULL, INDEX | |
| `otp_hash` | VARCHAR(64) | NOT NULL | SHA-256. Never the plain OTP |
| `purpose` | VARCHAR(30) | NOT NULL | `REGISTRATION`, `LOGIN`, `PASS_RETRIEVAL` |
| `expires_at` | TIMESTAMP | NOT NULL | Created time + 10 minutes |
| `attempts` | INT | DEFAULT 0 | Locked at 5 |
| `consumed` | BOOLEAN | DEFAULT FALSE | A verified OTP cannot be reused |

Rate limit: at most 3 OTP requests per email per 15 minutes (FR-REG-7). Requesting a new OTP invalidates the previous one.

### `blocklist`

| Column | Type | Constraint | Notes |
|--------|------|-----------|-------|
| `id` | BIGSERIAL | PK | |
| `campus_id` | BIGINT | NOT NULL | Blocklists are per campus |
| `email` | VARCHAR(150) | NULLABLE | One of email or phone must be set |
| `phone` | VARCHAR(20) | NULLABLE | |
| `reason` | VARCHAR(255) | NOT NULL | Mandatory |
| `created_by` | BIGINT | NOT NULL | Campus Admin user id |
| `created_at` | TIMESTAMP | DEFAULT NOW() | |

A blocked registration is rejected with a **non-specific** message that does not reveal the block (FR-BLK-4). Adding someone to the blocklist must also revoke any active pass they hold, by calling gatepass-service (FR-BLK-6).

### `audit_logs`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGSERIAL | PK |
| `actor_user_id` | BIGINT | NULL for anonymous attempts |
| `actor_role` | VARCHAR(20) | |
| `action` | VARCHAR(60) | `LOGIN_SUCCESS`, `LOGIN_FAILED`, `LOGOUT`, `OTP_REQUEST`, `APPROVAL`, `REJECTION`, `PASS_REVOKED`, `BLOCKLIST_ADD`, `CONFIG_CHANGE`, … |
| `target_entity` | VARCHAR(100) | e.g. `gate_pass:412` |
| `campus_id` | BIGINT | Scopes the Campus Admin's view |
| `source_ip` | VARCHAR(45) | IPv6-safe length |
| `created_at` | TIMESTAMP | |

Append-only. No interface may edit or delete an entry. Never write a password, OTP, or QR token into this table (FR-AUD-4, FR-AUD-5).

---

## UserDB (PostgreSQL)

Owns student and faculty profile information.

| Table | What it stores | Why |
|-------|---------------|-----|
| `student_profiles` | Roll no, year, national ID, address, photo object key | Student identity — the photo file is in object storage, only the key lives here |
| `faculty_profiles` | Employee ID, designation, qualification | Faculty identity, same pattern |
| `departments` | Per-campus department list | Created by each Campus Admin at onboarding |
| `documents` | Object key, file name, mime type, verified flag | ID and certificate uploads — actual files in object storage |

**Notes**

- **No `semester` column, and no semester field in any UI.** Academic scheduling data is not needed for access control.
- **`departments` ships empty.** Rows are created by a Campus Admin, never seeded. Department dropdowns are populated from the API and allow no free-text entry (FR-PROF-9).
- Identity documents use generic columns — `national_id_type` and `national_id_number` — rather than a country-specific field name, since Perimity is not bound to one jurisdiction.
- Actual files live in object storage; the database stores only the key.
- `user_id` references the user in AuthDB (cross-service reference by convention, not a DB-enforced foreign key).

**Upload constraints** (FR-PROF-6 … FR-PROF-8): photos JPEG or PNG up to 2 MB; documents PDF, JPEG, or PNG up to 5 MB. Validate the actual file content type on the server, not the declared one. Object keys are generated server-side — never accept a client-supplied storage path.

**Sensitive-field rule.** Changing `name`, photo, or roll number sets the profile to pending and pauses the holder's pass until faculty re-approval. This service detects the change; gatepass-service owns the pass state. Call its API (FR-PROF-3).

---

## GatePassDB (PostgreSQL)

Owns the visitor request workflow and the gate pass lifecycle. This is the core business logic.

| Table | What it stores | Why |
|-------|---------------|-----|
| `visitor_requests` | Visitor name, email, purpose, host, dates, OTP status, approval status | Full visitor registration form data before a pass is issued |
| `gate_passes` | Pass holder, campus, pass type, event, valid dates, status, object keys for QR + PDF | The actual gate pass record |
| `events` | Name, campus, date range, created_by | A programme with many attendees |
| `bulk_upload_batches` | Excel object key, row counts, processing status | Tracks faculty bulk imports |

### `gate_passes` — key columns

| Column | Notes |
|--------|-------|
| `holder_user_id` | The person |
| `campus_id` | Tenant scope |
| `pass_type` | `DAILY` or `EVENT` |
| `event_id` | NULL for daily passes, set for event passes |
| `valid_from`, `valid_to` | `valid_to` is NULL for a standing daily pass |
| `status` | See the state machine below |
| `revoked_reason` | Mandatory when status is `REVOKED` |
| `qr_key`, `pdf_key` | Object storage keys, filled in by qr-service |

### Pass state machine

| State | Meaning | Scan result |
|-------|---------|-------------|
| `PENDING` | Approved; QR/PDF generation not yet complete | Red — pass not ready |
| `ACTIVE` | Valid and in date range | Green |
| `PAUSED` | Holder changed a sensitive profile field; awaiting re-approval | Red — pass paused |
| `EXPIRED` | Validity end date has passed | Red — pass expired |
| `REVOKED` | Withdrawn by an administrator, or by blocklisting | Red — pass revoked |

Legal transitions, and no others:

```
PENDING → ACTIVE
ACTIVE  → PAUSED → ACTIVE
ACTIVE  → EXPIRED
ACTIVE  → REVOKED
PAUSED  → REVOKED
```

`PAUSED` is new in v1.1 and is required by FR-PROF-3 / FR-PASS-4.

Expiry is **not** automatic in the database. A scheduled job in this service must flip `ACTIVE → EXPIRED` at least once a day (FR-PASS-3).

Re-issuing a pass invalidates the previous QR token and generates a new one (FR-PASS-5). Revoked and expired records are retained for audit and never deleted (FR-PASS-7).

**One identity, many passes.** A student can simultaneously hold a `DAILY` pass with no end date and an `EVENT` pass valid only for the event dates. Both active at once is normal.

---

## CampusDB (PostgreSQL)

Owns multi-tenant campus management.

| Table | What it stores | Why |
|-------|---------------|-----|
| `campuses` | Name, code, address, logo object key, admin user, is_suspended | One row per tenant institution |
| `campus_gates` | Gate name, location per campus | A guard is bound to one gate per session |
| `campus_config` | Key-value settings per campus | Each campus has its own rules without a schema change |

### `campus_config` keys (v1.1 set)

| Key | Type | Default | Effect |
|-----|------|---------|--------|
| `visitor_approval_required` | boolean | true | If false, a verified visitor request issues a pass without approval |
| `repeat_entry_result` | `GREEN` / `AMBER` | `AMBER` | Result shown when a holder is scanned a second time the same day |
| `daily_pass_validity_days` | integer | 365 | Validity window of a student daily pass |
| `max_visitor_duration_days` | integer | 7 | Maximum length of a single visitor pass |
| `otp_expiry_minutes` | integer | 10 | OTP validity window |
| `photo_required_for_pass` | boolean | true | Whether a pass may be issued without a holder photo |

Unset keys fall back to the documented default. Values are validated against their declared type before saving. Every change is written to the audit log with the previous and new value (FR-CFG-3 … FR-CFG-5).

`repeat_entry_result` is what tells the Guard Service whether a second scan on the same day shows green or amber. Guard Service is blocked until this key exists.

**Other notes**

- Suspending a campus keeps all its data readable, never deletes it (FR-ADM-10).
- A department or gate that is referenced by an existing profile, pass, or entry log cannot be deleted; mark it inactive instead (FR-ADM-8).
- A single deployment serves any number of campuses.

---

## EntryLogDB (MongoDB)

Owns high-volume, append-only gate scan events.

| Collection | What it stores | Why |
|-----------|---------------|-----|
| `entry_logs` | Scan result, pass id, holder, guard id, gate, event, timestamp, device | Every QR scan creates one document — allow *and* deny both recorded |

**Why MongoDB here**

- Write-heavy — every scan is an insert.
- No joins needed — flat document per scan.
- High volume — millions of rows over time.
- Shardable by `campus_id` as volume grows.
- Queried mostly by simple filters (campus, date range, pass holder).

**Indexes**

- Compound: `campus_id + scanned_at` (covers most queries)
- Single: `pass_id`, `holder_user_id`, `event_id`
- Optional TTL on `scanned_at`; retain at least twelve months

**Result values:** `GREEN`, `RED`, `AMBER`. A red document also carries `deny_reason` — one of `EXPIRED`, `REVOKED`, `PAUSED`, `PENDING`, `INVALID_TOKEN`, `WRONG_CAMPUS`.

**Redis.** Active-pass lookups are cached in Redis so a scan returns in under one second (FR-SCAN-3). This is required, not optional. Invalidate the cache entry whenever a pass changes state.

**Entry only.** No exit scan, no in/out toggle, anywhere. Repeat entries on the same day are each logged as separate documents, exactly like separate lines in a paper register.

---

## QRDB (PostgreSQL)

Owns pass tokens and QR/PDF generation jobs.

| Table | What it stores | Why |
|-------|---------------|-----|
| `qr_records` | Token hash, pass id, object keys for QR + PDF, valid dates, is_active | The validated pass token — the guard's scan checks this |
| `generation_jobs` | Batch id, job status, retry count, error message | Tracks async QR generation jobs from the RabbitMQ queue |

**Notes**

- The QR token is AES-256 encrypted (pass_id + campus_id + expiry) and contains **no personal data**.
- Re-issuing a pass sets the old `qr_record.is_active = false` so the previous QR stops validating immediately.
- Failed generation jobs are retried a bounded number of times, then flagged for manual attention (FR-QR-5).
- `generation_jobs.batch_id` is what powers the bulk progress screen.

---

## How QR and PDF Get Stored — Step by Step

1. An approver approves a visitor request or student registration.
2. Gate Pass Service creates a `gate_passes` record with status `PENDING` and no QR yet.
3. Gate Pass Service drops a job into RabbitMQ: `{ pass_id, holder_name, campus_id, valid_from, valid_to, batch_id }`.
4. QR Service consumes the job.
5. It generates a unique signed token (AES-256: pass_id + campus_id + expiry), a QR PNG from the token, and a PDF pass containing the QR, holder name, photo if available, and validity dates.
6. It uploads both files to object storage:
   - `passes/campus-1/pass-123-qr.png`
   - `passes/campus-1/pass-123.pdf`
7. It stores the object keys and the token hash in QRDB.
8. It notifies Gate Pass Service, which sets `qr_key`, `pdf_key`, and `status = ACTIVE`.
9. QR Service emails the PDF to the holder, with campus and event names substituted from data — never a literal.
10. A guard scans the QR at the gate:
    - Guard Service decrypts the token
    - Checks the pass is active and in date range, via Redis first, QR Service second
    - Records the result in MongoDB `entry_logs`
    - Returns GREEN, RED, or AMBER to the scanner UI

---

## Object Storage Folder Structure

```
perimity-bucket/
├── campuses/
│   └── campus-1/
│       └── logo.png
├── profiles/
│   └── user-42/
│       ├── photo.jpg
│       └── id-document.pdf
├── passes/
│   └── campus-1/
│       ├── pass-123-qr.png   ← QR image
│       └── pass-123.pdf      ← printable PDF pass
└── bulk/
    └── campus-1/
        └── batch-88.xlsx     ← uploaded Excel, kept for the error report
```

---

## Where Everything Is Stored — Quick Reference

| Thing | Where stored |
|-------|-------------|
| User passwords | AuthDB — bcrypt hashed |
| OTP codes | AuthDB — SHA-256 hashed, never plain |
| Password reset tokens | AuthDB — hashed, single use, time limited |
| Blocklist entries | AuthDB |
| Audit trail | AuthDB — append only |
| Profile photos | Object storage — only the key in UserDB |
| Identity documents | Object storage — only the key in UserDB |
| Department list | UserDB — created per campus, never seeded |
| Campus settings | CampusDB — key-value per campus |
| QR code image | Object storage — key in GatePassDB and QRDB |
| PDF pass | Object storage — key in GatePassDB and QRDB |
| QR token | QRDB — AES-256 encrypted, never plain |
| Active-pass cache | Redis — invalidated on any pass state change |
| Gate scan events | MongoDB — one document per scan |
| Everything else | PostgreSQL |

---

## Key Design Principles

- **Database per service** — no service reads another service's database directly; they call each other's APIs.
- **Campus-agnostic** — no institution name, department list, or email domain anywhere in code or seed data.
- **Files in object storage, keys in the database** — never store binary files in the database.
- **Secrets always hashed** — passwords (bcrypt), OTPs (SHA-256), QR tokens (AES-256). No personal data in a QR code.
- **Multi-tenant by campus_id** — every campus-specific row carries a `campus_id`.
- **PostgreSQL for transactions, MongoDB for logs** — strong consistency where it matters, scale where volume grows.
- **Nothing is hard-deleted** — deactivate accounts, suspend campuses, retain expired and revoked passes.
