# Gate Pass Service

**Owner: Tushar**

Handles visitor requests, the gate pass lifecycle, events, and bulk upload
batches. Owns `gatepassdb` (PostgreSQL). Runs on port 8083.

This is the largest backend in the project — the bulk engine and mixed-attendee
resolution live here.

See `/docs` for the full schema and rules.

## Pass state machine — new in v1.1

A pass is in exactly one state. Only these transitions are legal:

```
PENDING → ACTIVE
ACTIVE  → PAUSED → ACTIVE
ACTIVE  → EXPIRED
ACTIVE  → REVOKED
PAUSED  → REVOKED
```

| State | Scan result |
|---|---|
| PENDING | red — pass not ready |
| ACTIVE | green |
| PAUSED | red — pass paused |
| EXPIRED | red — pass expired |
| REVOKED | red — pass revoked |

`PAUSED` did not exist in v1.0 and is required by FR-PROF-3 / FR-PASS-4.

You also need a **scheduled job** that flips `ACTIVE → EXPIRED` at least daily
(FR-PASS-3). Nothing does this today.

## What this service owns

| Area | Requirements |
|---|---|
| Approval workflow | FR-APPR-1 … FR-APPR-6 |
| Pass lifecycle | FR-PASS-1 … FR-PASS-7 |
| Bulk and events | FR-BULK-1 … FR-BULK-10 |
| Visitor self-service | FR-VIS-1 … FR-VIS-4 |

New since v1.0: **revoke with reason**, **scheduled expiry**, **pause/resume**,
**re-issue** (invalidates the old token), **Excel template download**, **row
limit**, **retry failed rows only**, **event cancellation** (revokes all its
passes).

## Frontend screens owned

| # | Screen |
|---|---|
| 6 | Visitor Registration |
| 7 | Approval Queue |
| 8 | My Pass |
| 12 | Attendance Dashboard |

Screen 8 also covers visitor self-service (FR-VIS): a holder enters their email,
verifies an OTP, and sees their passes again. Show status for non-active passes
without exposing the QR.

Folder: `frontend/src/gatepass/`

⚠️ Screens 9, 10, and 11 (Bulk Upload, Bulk Progress, Event Management) are
built by **Arham** but call **this service's APIs**. Those endpoints must be
ready by Day 11 so Arham is unblocked in week 3.

## Next steps

Once you have Spring Boot code here, add a `Dockerfile` in this folder and
uncomment this service's block in the root `docker-compose.yml`.
