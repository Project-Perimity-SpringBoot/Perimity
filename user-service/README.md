# User Service

Owner: Mukul

Handles student/faculty profiles, departments, and documents. Owns UserDB
(PostgreSQL, port 5433). See `/docs` for the full schema and rules.

Reminder: no `Semester` field anywhere in the UI.

Once you have Spring Boot code here, add a `Dockerfile` in this folder and
uncomment this service's block in the root `docker-compose.yml`.
