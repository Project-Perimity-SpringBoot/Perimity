# Guard Service

**Owner: Palash**

Handles gate scan events (entry logs). Owns EntryLogDB (MongoDB, port 27017).
See `/docs` for the full schema and rules, including the GREEN/RED/AMBER scan
logic and the Behavior 2 auto-attribution rule.

This is the only service using MongoDB rather than PostgreSQL.

## Frontend screens owned

| # | Screen |
|---|---|
| 13 | Guard Scanner (full-screen GREEN / RED / AMBER result card) |
| 15 | Guard Log |

Folder: `frontend/src/guard/`

## Next steps

Once you have Spring Boot code here, add a `Dockerfile` in this folder and
uncomment this service's block in the root `docker-compose.yml`.
