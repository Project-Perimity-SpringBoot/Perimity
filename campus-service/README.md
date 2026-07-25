# Campus Service

**Owner: Arham**

Handles campuses, gates, departments-per-campus config, and Super Admin
operations. Owns `campusdb` (PostgreSQL). Runs on port 8084.

See `/docs` for the full schema and rules.

## This service carries the campus-agnostic promise

Perimity ships with **no** campus, **no** department list, and **no** email
domain. Everything institution-specific is created here at onboarding time. If
any other service hardcodes a campus name, the multi-tenant claim is false.

Seed only a single neutral demo campus (for example "Demo Campus") for the
presentation, never a real institution.

## What this service owns

| Area | Requirements |
|---|---|
| Campus and user administration | FR-ADM-1 … FR-ADM-10 |
| Campus policy configuration | FR-CFG-1 … FR-CFG-5 |

New since v1.0: **campus policy config** as key-value pairs, **deactivate /
reactivate accounts** (never hard-delete), **revoke passes on deactivation**,
**block deletion of a referenced department or gate**, **transfer Campus Admin
role**, **suspended campus stays readable**.

### Config keys to support (v1.1 set)

| Key | Type | Default |
|---|---|---|
| `visitor_approval_required` | boolean | true |
| `repeat_entry_result` | `GREEN` / `AMBER` | `AMBER` |
| `daily_pass_validity_days` | integer | 365 |
| `max_visitor_duration_days` | integer | 7 |
| `otp_expiry_minutes` | integer | 10 |
| `photo_required_for_pass` | boolean | true |

`repeat_entry_result` is what tells Palash's scanner whether a second scan on the
same day shows green or amber. He is blocked on this key existing.

## Frontend screens owned

| # | Screen | Calls whose API |
|---|---|---|
| 16 | Campus Admin | campus-service (own) |
| 19 | Campus Settings | campus-service (own) |
| 9 | Bulk Upload | **gatepass-service** (Tushar) |
| 10 | Bulk Progress | **qr-service** (Sanjay) |
| 11 | Event Management | **gatepass-service** (Tushar) |

Folders: `frontend/src/campus/` and `frontend/src/gatepass/` (for screens 9–11)

⚠️ Screens 9–11 depend on Tushar's bulk/event endpoints (Day 11) and Sanjay's
progress endpoint (Day 16). Coordinate with both during week 3.

## Next steps

Once you have Spring Boot code here, add a `Dockerfile` in this folder and
uncomment this service's block in the root `docker-compose.yml`.
