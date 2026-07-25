# QR Service

**Owner: Sanjay**

Handles QR tokens, PDF generation jobs, and outbound pass emails. Owns `qrdb`
(PostgreSQL). Runs on port 8086.

This is the most library-heavy backend: RabbitMQ consumer, AES-256 token
encryption, QR image generation, PDF generation, and object-storage uploads.
Fewer frontend screens to compensate.

See `/docs` for the full schema and rules.

## No institution strings in emails

Every campus-facing value in an email template is a substitution variable
resolved from the campus record at send time — `{campus_name}`, `{event_name}`,
`{event_dates}`, `{gate_name}`. No institution name may be written into a
template literal.

## What this service owns

| Area | Requirements |
|---|---|
| QR and pass generation | FR-QR-1 … FR-QR-5 |
| Notifications | FR-NOT-1 … FR-NOT-6 |

New since v1.0: **token invalidation on re-issue** (FR-PASS-5 — when gatepass
re-issues a pass, the old token must stop validating), **bounded email retry
with failure flagging** (FR-NOT-5), **never put an OTP, password, or raw token in
an email body** beyond the six-digit code itself (FR-NOT-6).

Notification is not a separate service. Pass, approval, and rejection emails are
sent from here; OTP and password-reset emails are sent from auth-service.

## Frontend screens owned

| # | Screen | Calls whose API |
|---|---|---|
| 14 | Pass Download | qr-service (own) |
| 20 | Super Admin Console | **campus-service** (Arham) |

Screen 20 is new in v1.1: create and suspend campuses, create Campus Admin
accounts, view platform-wide statistics across all campuses. There was no
Super Admin screen in the original 16, even though the role has existed in the
SRS since v1.0.

Folder: `frontend/src/qr/` and `frontend/src/campus/superadmin/`

⚠️ Arham's Bulk Progress screen (10) calls this service's
`/api/qr/jobs/batch/{batchId}/progress` endpoint. Have it ready by Day 16.

## Next steps

Once you have Spring Boot code here, add a `Dockerfile` in this folder and
uncomment this service's block in the root `docker-compose.yml`.
