# Guard Service

Owner: Palash

Handles gate scan events (entry logs). Owns EntryLogDB (MongoDB, port
27017). See `/docs` for the full schema and rules, including the
GREEN/RED/AMBER scan logic.

Once you have Spring Boot code here, add a `Dockerfile` in this folder and
uncomment this service's block in the root `docker-compose.yml`.
