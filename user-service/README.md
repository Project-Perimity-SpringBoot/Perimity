# User Service

**Owner: Mukul**

Handles student/faculty profiles, departments, and documents. Owns UserDB
(PostgreSQL, port 5433). See `/docs` for the full schema and rules.

**Reminder: no `Semester` field anywhere in the UI.**

## Frontend screens owned

| # | Screen |
|---|---|
| 3 | Student Profile |
| 4 | Faculty Profile |
| 5 | Student Directory |

Folder: `frontend/src/users/`

## Next steps

Once you have Spring Boot code here, add a `Dockerfile` in this folder and
uncomment this service's block in the root `docker-compose.yml`.
