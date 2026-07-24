# Gate Pass Service

**Owner: Tushar**

Handles visitor requests, the gate pass lifecycle, events, and bulk upload
batches. Owns GatePassDB (PostgreSQL, port 5434). See `/docs` for the full
schema and rules.

This is the largest backend in the project — the bulk engine and
mixed-attendee resolution live here.

## Frontend screens owned

| # | Screen |
|---|---|
| 6 | Visitor Registration |
| 7 | Approval Queue |
| 8 | My Pass |
| 12 | Attendance Dashboard |

Folder: `frontend/src/gatepass/`

⚠️ Screens 9, 10, and 11 (Bulk Upload, Bulk Progress, Event Management) are
built by **Arham** but call **this service's APIs**. Those endpoints must be
ready by Day 11 so Arham is unblocked in week 3.

## Next steps

Once you have Spring Boot code here, add a `Dockerfile` in this folder and
uncomment this service's block in the root `docker-compose.yml`.
