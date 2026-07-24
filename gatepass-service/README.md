# Gate Pass Service

Owner: Tushar

Handles visitor requests, the gate pass lifecycle, and bulk upload batches.
Owns GatePassDB (PostgreSQL, port 5434). See `/docs` for the full schema
and rules.

Once you have Spring Boot code here, add a `Dockerfile` in this folder and
uncomment this service's block in the root `docker-compose.yml`.
