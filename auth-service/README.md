# Auth Service

Owner: TBD

Handles login, users, OTP, and audit logs. Owns AuthDB (PostgreSQL, port 5432).
See `/docs` for the full schema and rules.

Once you have Spring Boot code here, add a `Dockerfile` in this folder and
uncomment this service's block in the root `docker-compose.yml`.
