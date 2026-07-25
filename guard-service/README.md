# Guard Service

**Owner: Palash**

Handles gate scan events and entry logs. Owns `entrylogdb` (MongoDB). Runs on
port 8085.

This is the only service using MongoDB rather than PostgreSQL.

See `/docs` for the full schema and rules, including the GREEN / RED / AMBER
scan logic and the Behavior 2 event auto-attribution rule.

## Entry only

No exit scan. No in/out toggle. Do not add one to the backend or the scanner UI
under any circumstances — an earlier draft table mentioned "select entry/exit"
and it was wrong. Repeat entries in a day are all logged as separate rows,
exactly like separate lines in a paper register.

## Amber is now defined

v1.0 left amber ambiguous. In v1.1 it is driven by the campus config key
`repeat_entry_result` (`GREEN` or `AMBER`, default `AMBER`), read from
campus-service. Either way, the entry is still logged.

## What this service owns

| Area | Requirements |
|---|---|
| Scanning and entry logging | FR-SCAN-1 … FR-SCAN-11 |

New since v1.0: **Redis cache** for active-pass lookups (required to hit the
sub-second target — this is not optional), **configurable repeat-entry result**,
**holder summary on the result card** (name, photo, pass type, validity),
**distinct error states** for camera unavailable / network down / unreadable QR,
which are not the same as a red denial.

## Frontend screens owned

| # | Screen | Calls whose API |
|---|---|---|
| 13 | Guard Scanner (full-screen GREEN / RED / AMBER result card) | guard-service (own) |
| 15 | Guard Log | guard-service (own) |
| 18 | Audit Log | **auth-service** (Omkar) |

Screen 18 is new in v1.1. It is a searchable, filterable, paginated table —
structurally the same as screen 15, which is why it sits with you. Super Admins
see all campuses; Campus Admins see their own only.

Folder: `frontend/src/guard/` and `frontend/src/auth/audit/`

## Next steps

Once you have Spring Boot code here, add a `Dockerfile` in this folder and
uncomment this service's block in the root `docker-compose.yml`.
