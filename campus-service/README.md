# Campus Service

**Owner: Arham**

Handles campuses, gates, and per-campus config. Owns CampusDB (PostgreSQL,
port 5435). See `/docs` for the full schema and rules.

This is the lightest backend in the project, so this owner also builds three
frontend screens for other services (see below).

## Frontend screens owned

| # | Screen | Calls whose API |
|---|---|---|
| 16 | Campus Admin | campus-service (own) |
| 9 | Bulk Upload | **gatepass-service** (Tushar) |
| 10 | Bulk Progress | **qr-service** (Sanjay) |
| 11 | Event Management | **gatepass-service** (Tushar) |

Folders: `frontend/src/campus/` and `frontend/src/gatepass/` (for screens 9–11)

⚠️ Screens 9–11 depend on Tushar's bulk/event endpoints (Day 11) and Sanjay's
progress endpoint (Day 16). Coordinate with both during week 3.

## Next steps

Once you have Spring Boot code here, add a `Dockerfile` in this folder and
uncomment this service's block in the root `docker-compose.yml`.
