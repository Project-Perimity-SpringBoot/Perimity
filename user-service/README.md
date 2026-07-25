# User Service

**Owner: Mukul**

Handles student and faculty profiles, departments, and documents. Owns `userdb`
(PostgreSQL). Runs on port 8082.

See `/docs` for the full schema and rules.

## Two rules that are easy to get wrong

**No `Semester` field anywhere in the UI**, even though it may exist in a
schema. Academic scheduling data is not needed for access control.

**Departments are campus data, not a fixed list.** Do not seed department names
in a migration or hardcode them in a dropdown. A Campus Admin creates the
department list for their campus after onboarding, and this service serves it
back. The dropdown is populated from the API and allows no free-text entry
(FR-PROF-9).

## What this service owns

| Area | Requirements |
|---|---|
| Profiles | FR-PROF-1 … FR-PROF-9 |

New since v1.0: **file upload limits** (photos JPEG/PNG up to 2 MB, documents
PDF/JPEG/PNG up to 5 MB), **server-side content-type validation**,
**server-generated object keys** — never accept a client-supplied storage path.

Changing a sensitive field (name, photo, roll number) must pause the holder's
pass until faculty re-approval. This service detects the change; gatepass-service
owns the pass state. Call its API.

## Frontend screens owned

| # | Screen | Calls whose API |
|---|---|---|
| 3 | Student Profile | user-service (own) |
| 4 | Faculty Profile | user-service (own) |
| 5 | Student Directory | user-service (own) |
| 17 | Blocklist | **auth-service** (Omkar) |

Screen 17 is new in v1.1: a Campus Admin view to add, search, and remove
blocklisted emails and phone numbers, each with a mandatory reason.

Folder: `frontend/src/users/` and `frontend/src/auth/blocklist/`

## Next steps

Once you have Spring Boot code here, add a `Dockerfile` in this folder and
uncomment this service's block in the root `docker-compose.yml`.
