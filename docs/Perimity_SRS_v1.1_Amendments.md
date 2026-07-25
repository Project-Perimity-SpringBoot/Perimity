# Perimity SRS — Amendment Pack for Version 1.1

**Purpose:** This document lists every change to be applied to *Perimity SRS v1.0* (dated 21 July 2026).
Two kinds of change are included:

- **Part A — Architecture correction:** five microservices → **six microservices**.
- **Parts B–E — Missing features:** functions referenced elsewhere in the project but never specified in the SRS.

Each item states **where** in the SRS it goes and gives the **exact replacement text**.

---

# PART A — Six Microservices (Architecture Correction)

## A.1 Why this change

SRS v1.0 §2.1 states *"five backend microservices."* This is inconsistent with:

- `Perimity_Database_Design.md`, which defines **six** service-owned databases.
- The team structure — six members, one service each, matching the feature-branch workflow.
- §2.5, which requires the Notification function but gives it no home service.

Version 1.1 standardises on **six backend microservices** plus an API Gateway (infrastructure, not counted as a business service).

## A.2 REPLACE — Section 2.1, paragraph 2

> Perimity replaces this with a microservices-based platform. A single deployment serves multiple campuses; each campus's data is scoped by a campus identifier. The system is composed of **six backend microservices** coordinated by an API gateway, a React web frontend, and supporting infrastructure for messaging, caching, and object storage. Each microservice owns its own database and no service reads another service's database directly; services communicate over REST and exchange asynchronous work through a message queue. The product is designed so that the same containers can be lifted from a single-server deployment to a clustered cloud environment without code changes.

## A.3 INSERT — new Section 2.1.1, "Service Decomposition"

> **2.1.1 Service Decomposition**
>
> Perimity is decomposed into six backend microservices. Each is independently deployable, owns its own database, and exposes a REST API documented with Swagger/OpenAPI.

| # | Service | Runtime | Owns database | Responsibility |
|---|---------|---------|---------------|----------------|
| S1 | **Auth Service** | Java 17 / Spring Boot | AuthDB (PostgreSQL) | Registration, OTP generation and verification, login, JWT issue and refresh, blocklist screening, audit log |
| S2 | **User Service** | Java 17 / Spring Boot | UserDB (PostgreSQL) | Student and faculty profiles, departments, document and photo metadata |
| S3 | **Campus Service** | Java 17 / Spring Boot | CampusDB (PostgreSQL) | Campuses, gates, campus policy configuration, Super Admin operations |
| S4 | **Gate Pass Service** | Java 17 / Spring Boot | GatePassDB (PostgreSQL) | Visitor requests, approval workflow, gate pass lifecycle, events, bulk onboarding |
| S5 | **Guard Service** | Java 17 / Spring Boot | EntryLogDB (MongoDB) | Gate scan validation, entry logging, attendance queries |
| S6 | **QR/PDF Service** | Python | QRDB (PostgreSQL) | Token generation, QR image, PDF pass, object-store upload, notification email dispatch |

> **Supporting components (not counted as business microservices):**
>
> - **API Gateway** (Spring Cloud Gateway) — single entry point, JWT validation, request routing.
> - **RabbitMQ** — asynchronous job queue between S4 and S6.
> - **Redis** — cache for active-pass lookups used by S5.
> - **Object store (S3/MinIO)** — photos, QR images, PDF passes.
>
> **Notification** is not a separate service in Version 1.1. Email dispatch is implemented as a shared internal module used by S1 (OTP and password-reset emails) and S6 (approval, rejection, and pass-delivery emails). This keeps the service count at six while satisfying all FR-NOT requirements.

## A.4 REPLACE — Section 2.5, bullet 2

> - The architecture must follow a microservices pattern comprising six independently deployable backend services, each owning its own database, and must use a message queue for asynchronous communication.

## A.5 UPDATE — Appendix B.2, Entity Relationship Diagram

The current ER diagram groups entities into three databases (IdentityDB, CampusPassDB, EntryLogDB). Re-label the groupings to the six service-owned databases:

| Entity | Move to |
|--------|---------|
| `USER`, `OTP_VERIFICATION`, `BLOCKLIST`, `AUDIT_LOG` | **AuthDB** |
| `STUDENT_PROFILE`, `FACULTY_PROFILE`, `DEPARTMENT`, `DOCUMENT` | **UserDB** |
| `CAMPUS`, `CAMPUS_GATE`, `CAMPUS_CONFIG` | **CampusDB** |
| `VISITOR_REQUEST`, `GATE_PASS`, `EVENT`, `BULK_UPLOAD_BATCH` | **GatePassDB** |
| `QR_RECORD`, `GENERATION_JOB` | **QRDB** |
| `ENTRY_LOG` | **EntryLogDB (MongoDB)** |

Add a note under the figure:

> Cross-database references (for example `student_profile.user_id` → `users.id`) are references by convention, resolved through service APIs. They are not database-enforced foreign keys, because the two tables live in different service-owned databases.

## A.6 Deployment note to add to Section 2.4

> To conserve resources on a single EC2 instance, the five PostgreSQL databases may be co-hosted on fewer PostgreSQL container instances during development, provided each service continues to connect only to its own logical database and holds no credentials for any other. The database-per-service boundary is logical and must be preserved in code regardless of physical co-hosting.

---

# PART B — New System Features

Add these as new subsections after §4.10.

## B.1 INSERT — Section 4.11, Blocklist Management

> **4.11 Blocklist Management**
>
> **4.11.1 Description and Priority**
>
> Description: Each campus maintains a blocklist of banned email addresses and phone numbers. Entries on the blocklist are rejected automatically during registration and skipped during bulk upload. Campus Admins manage the list for their own campus.
>
> Priority: High
>
> **4.11.2 Stimulus/Response Sequences**
>
> - A Campus Admin opens the blocklist view, adds an email or phone number with a reason, and saves.
> - A blocked person attempts to register; the system rejects the attempt with a generic message and records the attempt in the audit log.
> - A Campus Admin removes an entry; that person can register normally thereafter.
>
> **4.11.3 Functional Requirements**
>
> - **FR-BLK-1** The system shall allow a Campus Admin to add an email address or phone number to the blocklist of their own campus, together with a mandatory reason.
> - **FR-BLK-2** The system shall allow a Campus Admin to view, search, and remove blocklist entries for their own campus.
> - **FR-BLK-3** The system shall check every registration and every bulk-upload row against the blocklist of the target campus before proceeding.
> - **FR-BLK-4** The system shall reject a blocked registration with a non-specific message that does not reveal that the person is blocklisted.
> - **FR-BLK-5** The system shall record every blocklist addition, removal, and blocked-registration attempt in the audit log.
> - **FR-BLK-6** The system shall revoke any active gate pass held by a person at the moment their identity is added to the blocklist.

*Rationale: FR-REG-3 and §5.3 both assume a blocklist exists, and the ER diagram contains a `BLOCKLIST` entity, but no feature section defined who maintains it or what happens to passes already issued.*

## B.2 INSERT — Section 4.12, Gate Pass Lifecycle Management

> **4.12 Gate Pass Lifecycle Management**
>
> **4.12.1 Description and Priority**
>
> Description: A gate pass moves through a defined set of states from creation to end of life. This feature covers revocation, expiry, pausing on profile change, and re-issue. It is distinct from §4.5, which covers only the initial generation of a pass.
>
> Priority: High
>
> **4.12.2 Pass States**
>
> | State | Meaning | Scan result |
> |-------|---------|-------------|
> | `PENDING` | Approved; QR/PDF generation not yet complete | Red — pass not ready |
> | `ACTIVE` | Valid and in date range | Green |
> | `PAUSED` | Holder changed a sensitive profile field; awaiting re-approval | Red — pass paused |
> | `EXPIRED` | Validity end date has passed | Red — pass expired |
> | `REVOKED` | Withdrawn by an administrator or by blocklisting | Red — pass revoked |
>
> Permitted transitions: `PENDING → ACTIVE`; `ACTIVE → PAUSED → ACTIVE`; `ACTIVE → EXPIRED`; `ACTIVE → REVOKED`; `PAUSED → REVOKED`. No other transition is permitted.
>
> **4.12.3 Functional Requirements**
>
> - **FR-PASS-1** The system shall maintain a gate pass in exactly one of the states `PENDING`, `ACTIVE`, `PAUSED`, `EXPIRED`, or `REVOKED`, and shall permit only the transitions listed in §4.12.2.
> - **FR-PASS-2** The system shall allow a Campus Admin or the issuing Faculty to revoke a gate pass, recording a mandatory reason.
> - **FR-PASS-3** The system shall automatically set a pass to `EXPIRED` once its validity end date has passed, by means of a scheduled job that runs at least once per day.
> - **FR-PASS-4** The system shall set a pass to `PAUSED` when the holder changes a sensitive profile field, and shall restore it to `ACTIVE` only on faculty re-approval.
> - **FR-PASS-5** The system shall allow an approver to re-issue a pass, which invalidates the previous QR token and generates a new one.
> - **FR-PASS-6** The system shall notify the holder by email whenever their pass is revoked, paused, or re-issued.
> - **FR-PASS-7** The system shall retain revoked and expired pass records for audit and shall not delete them.

*Rationale: the class diagram shows `revoke()` on `GatePass` and §5.5 requires a pass to be paused on sensitive profile change, but no functional requirement defined the states, who may revoke, or how expiry actually occurs.*

## B.3 INSERT — Section 4.13, Audit Log and Security Review

> **4.13 Audit Log and Security Review**
>
> **4.13.1 Description and Priority**
>
> Description: The system records security-relevant actions in an immutable audit log. Super Admins review the log across all campuses; Campus Admins review the log for their own campus only.
>
> Priority: Medium
>
> **4.13.2 Functional Requirements**
>
> - **FR-AUD-1** The system shall record an audit entry for each of the following: successful login, failed login, logout, OTP request, OTP failure, approval, rejection, pass revocation, blocklist change, account creation, account deactivation, and campus configuration change.
> - **FR-AUD-2** Each audit entry shall record actor user id, actor role, action, target entity, campus id, source IP address, and timestamp.
> - **FR-AUD-3** The system shall present a searchable, filterable, paginated audit log view to Super Admins across all campuses and to Campus Admins for their own campus only.
> - **FR-AUD-4** Audit entries shall be append-only; the system shall provide no interface to edit or delete them.
> - **FR-AUD-5** The system shall never write passwords, OTP values, or QR tokens into the audit log.

*Rationale: §5.3 requires an audit log and §2.3 states the Super Admin "views audit data", but no feature section defined what is logged or how it is viewed.*

## B.4 INSERT — Section 4.14, Campus Policy Configuration

> **4.14 Campus Policy Configuration**
>
> **4.14.1 Description and Priority**
>
> Description: Each campus can set its own operating rules without a code or schema change. Settings are stored as key-value pairs scoped by campus id.
>
> Priority: Medium
>
> **4.14.2 Configurable Settings (Version 1.0 set)**
>
> | Key | Type | Default | Effect |
> |-----|------|---------|--------|
> | `visitor_approval_required` | boolean | true | If false, a verified visitor request issues a pass without approval |
> | `repeat_entry_result` | enum (`GREEN` / `AMBER`) | `AMBER` | Result shown when a holder is scanned a second time on the same day |
> | `daily_pass_validity_days` | integer | 365 | Validity window of a student daily pass |
> | `max_visitor_duration_days` | integer | 7 | Maximum length of a single visitor pass |
> | `otp_expiry_minutes` | integer | 10 | OTP validity window |
> | `photo_required_for_pass` | boolean | true | Whether a pass may be issued without a holder photo |
>
> **4.14.3 Functional Requirements**
>
> - **FR-CFG-1** The system shall store campus settings as key-value pairs scoped by campus id.
> - **FR-CFG-2** The system shall allow a Campus Admin to view and change the settings of their own campus only.
> - **FR-CFG-3** The system shall apply a documented default value for any setting a campus has not configured.
> - **FR-CFG-4** The system shall validate a setting value against its declared type and permitted range before saving.
> - **FR-CFG-5** The system shall record every configuration change in the audit log, including the previous and new values.

*Rationale: `campus_config` exists in the database design but the SRS mentions only gates and departments. This section also resolves the amber ambiguity noted in §D.1 below.*

## B.5 INSERT — Section 4.15, Session and Credential Management

> **4.15 Session and Credential Management**
>
> **4.15.1 Description and Priority**
>
> Description: Covers logout, token lifetime, password reset, first-login password change, and the binding of a guard to a gate for the duration of a session.
>
> Priority: High
>
> **4.15.2 Functional Requirements**
>
> - **FR-SESS-1** The system shall issue an access token valid for a limited period and shall reject expired tokens at the API gateway.
> - **FR-SESS-2** The system shall provide a logout function that invalidates the current session and records the event in the audit log.
> - **FR-SESS-3** The system shall require a Guard to select their assigned gate at login, and shall bind every scan performed in that session to the selected gate.
> - **FR-SESS-4** The system shall require a Campus Admin, Faculty, or Guard to change their password on first login when the account was created by an administrator.
> - **FR-SESS-5** The system shall provide a forgot-password flow that emails a single-use, time-limited reset link, invalidates the link on use, and records the reset in the audit log.
> - **FR-SESS-6** The system shall enforce a minimum password policy of eight characters including at least one letter and one digit.
> - **FR-SESS-7** The system shall lock an account for a defined period after a configured number of consecutive failed login attempts.

*Rationale: FR-AUTH-7 names a forgot-password flow without defining it; §5.5 requires guard-to-gate binding without a functional requirement; logout, token lifetime, and password policy were entirely absent.*

## B.6 INSERT — Section 4.16, Visitor Pass Self-Service

> **4.16 Visitor Pass Self-Service**
>
> **4.16.1 Description and Priority**
>
> Description: A visitor who has lost or deleted their pass email can retrieve the pass again without contacting the campus. They enter their email, receive an OTP, and view or download their valid passes.
>
> Priority: Medium
>
> **4.16.2 Functional Requirements**
>
> - **FR-VIS-1** The system shall allow any pass holder to retrieve their valid passes by entering their email address and verifying a one-time password.
> - **FR-VIS-2** The system shall display only passes that are currently `ACTIVE`, and shall show the status of any pass that is pending, paused, expired, or revoked without exposing the QR code.
> - **FR-VIS-3** The system shall allow the holder to download the PDF pass and to have it re-sent to their registered email address.
> - **FR-VIS-4** The system shall record every pass retrieval in the audit log.

*Rationale: `Perimity_Event_Bulk_Design.md` states that an event visitor who loses the pass email can re-request it, but no SRS requirement covered this.*

---

# PART C — Amendments to Existing Sections

Add the following requirements to the sections named. Numbering continues from the last existing requirement in each section.

## C.1 Section 4.1 — Registration and OTP

- **FR-REG-7** The system shall limit OTP requests to a maximum of three per email address per fifteen-minute period and shall reject further requests within that window.
- **FR-REG-8** The system shall reject registration where the email address is already registered with a different role at the same campus.
- **FR-REG-9** The system shall discard unverified registration attempts that are not completed within twenty-four hours.

## C.2 Section 4.3 — Profile Management

- **FR-PROF-6** The system shall accept photo uploads only in JPEG or PNG format up to a maximum of 2 MB, and document uploads only in PDF, JPEG, or PNG format up to a maximum of 5 MB.
- **FR-PROF-7** The system shall validate the actual file content type on the server and shall reject any file whose content does not match its declared type.
- **FR-PROF-8** The system shall generate object-store keys server-side and shall never accept a client-supplied storage path.
- **FR-PROF-9** The system shall present department values from the department list configured for that campus only, and shall not permit free-text entry. Department names are campus-supplied data; the system defines no fixed list.

## C.3 Section 4.6 — Bulk Onboarding and Event Management

- **FR-BULK-7** The system shall provide a downloadable Excel template showing the required column headings.
- **FR-BULK-8** The system shall reject an upload exceeding a configured maximum row count and shall state the limit in the error message.
- **FR-BULK-9** The system shall display the progress of a bulk batch (queued, processing, completed, failed counts) and shall allow the uploader to retry only the failed rows.
- **FR-BULK-10** The system shall allow an event to be cancelled, which revokes all event passes issued for it and notifies the holders.

## C.4 Section 4.7 — Gate Scanning and Entry Logging

- **FR-SCAN-8** The system shall determine the result for a repeat entry on the same day according to the campus setting `repeat_entry_result`, and shall log the entry in either case.
- **FR-SCAN-9** The system shall display to the guard, alongside the scan result, the holder's name, photo, pass type, and validity dates.
- **FR-SCAN-10** The system shall show the guard a clear, actionable error state when the camera is unavailable, the network is unreachable, or the QR code is unreadable, distinct from a red denial result.
- **FR-SCAN-11** The system shall reject a token that has been tampered with or that fails decryption, and shall log the attempt with the reason `INVALID_TOKEN`.

## C.5 Section 4.9 — Campus and User Administration

- **FR-ADM-6** The system shall allow a Campus Admin to deactivate and reactivate Faculty and Guard accounts of their own campus. Accounts shall be deactivated, never hard-deleted.
- **FR-ADM-7** The system shall revoke all active passes held by a user when that user's account is deactivated.
- **FR-ADM-8** The system shall prevent deletion of a department or gate that is referenced by an existing profile, pass, or entry log; such records shall be marked inactive instead.
- **FR-ADM-9** The system shall allow a Super Admin to transfer the Campus Admin role of a campus to a different user.
- **FR-ADM-10** The system shall retain, and continue to display, all data of a suspended campus in read-only form.

## C.6 Section 4.10 — Notifications

- **FR-NOT-5** The system shall retry a failed email a bounded number of times and shall flag persistent failures for administrator attention.
- **FR-NOT-6** The system shall never include a password, an OTP value, or a raw QR token in the body of an email; OTP emails shall contain the six-digit code only, which is not stored in plain text.

## C.7 Section 5 — New Section 5.6, Availability and Operability

> **5.6 Availability and Operability**
>
> - Each service shall expose a health endpoint reporting its own status and the reachability of its database and message broker.
> - Each service shall emit structured logs including a correlation identifier that is propagated across service calls, so that a single user action can be traced end to end.
> - The failure of any single service shall not prevent gate scanning, which is the most time-critical operation.
> - Entry log documents shall be retained for a minimum of twelve months.
> - Database backups shall be taken at least daily and restoration shall be verified at least once before final deployment.
> - Configuration and secrets shall be supplied through environment variables and shall never be committed to version control.

---

# PART D — Appendix C: Replacement To Be Determined List

Appendix C currently states that no TBD items remain. Replace it with the list below; an SRS baseline with genuine open items recorded is stronger than one that claims none.

> **Appendix C: To Be Determined List**
>
> | # | Item | Owner | Target resolution |
> |---|------|-------|-------------------|
> | TBD-1 | Access token lifetime and whether refresh tokens are used in Version 1.0 | Auth Service owner | Before Sprint 2 |
> | TBD-2 | Maximum bulk-upload row count (FR-BULK-8) | Gate Pass Service owner | Before bulk feature build |
> | TBD-3 | Whether the guard scanner requires a degraded offline mode, or remains strictly online (§2.7 currently assumes connectivity) | Guard Service owner | Before Sprint 3 |
> | TBD-4 | Entry-log retention period beyond twelve months and whether a MongoDB TTL index is applied | Guard Service owner | Before deployment |
> | TBD-5 | Email provider for production — AWS SES or SMTP relay | DevOps owner | Before AWS deployment |
> | TBD-6 | Whether Super Admin can act on behalf of a Campus Admin for support purposes, and how such actions are audited | Campus Service owner | Before Sprint 3 |

---

# PART E — Impact by Service Owner

Six services, six owners. This table shows which amendments land on each.

| Service | New / changed requirements | Effort |
|---------|---------------------------|--------|
| **S1 Auth Service** | 4.11 Blocklist (FR-BLK-1..6), 4.13 Audit (FR-AUD-1..5), 4.15 Sessions (FR-SESS-1..7), FR-REG-7..9, FR-NOT-6 | **Heaviest** — three new feature areas |
| **S2 User Service** | FR-PROF-6..9 (file validation, department list) | Light |
| **S3 Campus Service** | 4.14 Campus config (FR-CFG-1..5), FR-ADM-6..10 | Medium |
| **S4 Gate Pass Service** | 4.12 Pass lifecycle (FR-PASS-1..7), FR-BULK-7..10, expiry scheduled job | **Heavy** — new state machine |
| **S5 Guard Service** | FR-SCAN-8..11, Redis cache, `repeat_entry_result` handling | Medium |
| **S6 QR/PDF Service** | FR-PASS-5 token invalidation on re-issue, FR-NOT-5 email retry | Medium |
| **Frontend (shared)** | Blocklist screen, audit log viewer, campus settings screen, pass-status badges, visitor self-service page, guard error states | **Heavy** — six new screens |

**Suggested build order:** FR-SESS and FR-AUD first (everything else logs to the audit trail), then FR-PASS (the pass state machine other features depend on), then FR-BLK and FR-CFG, then the reporting and self-service screens.

---

# PART F — De-branding: Remove All Institution-Specific References

Perimity is a campus-agnostic product. It is not built for, or tied to, any one institution. Every institution-specific reference must be removed from the product, the documents, the code, and the seed data.

## F.1 Guiding rule

> The system defines **no** campus names, **no** department lists, and **no** email domains. All of it is data supplied by a campus at onboarding time. Documentation examples use neutral placeholders.

This is not only a branding fix — it strengthens the multi-tenant claim in §2.1. A system that hardcodes one institution's course list is not genuinely multi-tenant.

## F.2 Changes in SRS v1.0

| Location | Current text | Replace with |
|----------|-------------|--------------|
| §2.5, semester bullet | "…must never appear in any user interface form, in accordance with C-DAC Mumbai conventions." | "…must never appear in any user interface form. Academic scheduling data is not required for access control and is deliberately excluded from all user-facing forms." |
| §2.5, department bullet | "Department values are C-DAC courses (DAC, DBDA, DESD, DITISS, DMLT, PGAIML)." | "Department values are campus-supplied data configured by each Campus Admin during onboarding. The system defines no fixed department list." |
| §4.3, FR-PROF-5 | (unchanged wording) | No change needed — it already says only that semester shall not be displayed. |

## F.3 Changes in `Perimity_Database_Design.md`

| Location | Current text | Replace with |
|----------|-------------|--------------|
| UserDB table, `departments` row | "DAC, DBDA, DESD etc per campus" | "Department list configured per campus" |
| UserDB notes | "No `semester` field anywhere — CDAC Mumbai has no semester concept." | "No `semester` field anywhere — academic scheduling data is out of scope for access control." |
| CampusDB table, `campuses` row | "One row per institution — CDAC Mumbai, CDAC Pune etc" | "One row per institution — each tenant campus" |

## F.4 Changes in `Perimity_Event_Bulk_Design.md`

| Location | Current text | Replace with |
|----------|-------------|--------------|
| Cross-campus attendees | "e.g. CDAC Pune student at CDAC Mumbai's event" | "e.g. a student of one tenant campus attending another tenant campus's event" |
| Welcome email subject | "Your gate pass for AI Summit at CDAC Mumbai" | "Your gate pass for {event_name} at {campus_name}" |
| Welcome email sign-off | "— CDAC Mumbai" | "— {campus_name}" |
| Welcome email body | "Welcome to AI Summit on Aug 10–12." | "Welcome to {event_name} on {event_dates}." |

Every campus-specific value in an email template becomes a substitution variable resolved from the `campuses` table at send time.

## F.5 Changes in code and seed data

| Item | Rule |
|------|------|
| Seed accounts | No `@cdac.in` addresses. Use `admin@example.com`, `guard@example.com`, or a domain read from an environment variable. |
| Seed departments | Do not ship a department list in migration or seed scripts. Departments are created by a Campus Admin through the UI after campus onboarding. |
| Seed campus | Seed a single demo campus named "Demo Campus" or similar, never a real institution. |
| Email templates | All campus-facing strings come from `campuses.name`, never a literal. |
| Frontend | Campus name and logo render from the API response for the logged-in user's campus. No institution name in any component, constant, or page title. |
| Repository | Project name is Perimity everywhere — repo name, package names, Docker image names, database names. |

## F.6 Two judgement calls to make as a team

**1. The SRS title page.** It currently reads *"Centre for Development of Advanced Computing (C-DAC), Mumbai — PG-DAC / PGCP-AC, February 2026 Batch."* This is **authorship metadata, not product branding** — it identifies who wrote the document, the same way an author's name appears on a paper. Removing it from an academic submission may be a problem, since evaluators expect the institution and batch identified.

Recommendation: keep the title page and revision history as they are, and remove institution references from everywhere else. If the team prefers a fully neutral document, move the authorship block to a separate cover sheet submitted alongside the SRS.

**2. Demo data for the presentation.** A campus-agnostic system still needs something on screen during the demo. Seed a fictional campus with a neutral name and a small invented department list created through the normal Campus Admin flow. Demonstrating the onboarding flow live is a stronger showing than a pre-baked campus, because it proves the multi-tenant claim rather than asserting it.

---

# Change Log Entry for the SRS Revision History

| Name | Date | Reason For Changes | Version |
|------|------|-------------------|---------|
| Perimity Team | *(insert date)* | Corrected architecture to six microservices; added blocklist management, pass lifecycle, audit review, campus configuration, session management, and visitor self-service; added file-upload, rate-limit, and operability requirements; removed all institution-specific references so that campus and department data is tenant-supplied; replaced Appendix C with an active TBD list | 1.1 |
