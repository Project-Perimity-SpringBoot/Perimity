-- Perimity — creates one database per service on the shared Postgres container.
--
-- The official postgres image runs every .sql file in
-- /docker-entrypoint-initdb.d/ exactly once, on FIRST startup only, when the
-- data volume is empty. If you have already started Postgres before adding this
-- file, run `docker compose down -v` once to drop the volume, then start again.
--
-- The database-per-service rule still applies. Each service connects only to
-- its own database below and must never query another service's tables.

CREATE DATABASE authdb;
CREATE DATABASE userdb;
CREATE DATABASE gatepassdb;
CREATE DATABASE campusdb;
CREATE DATABASE qrdb;

GRANT ALL PRIVILEGES ON DATABASE authdb     TO perimity;
GRANT ALL PRIVILEGES ON DATABASE userdb     TO perimity;
GRANT ALL PRIVILEGES ON DATABASE gatepassdb TO perimity;
GRANT ALL PRIVILEGES ON DATABASE campusdb   TO perimity;
GRANT ALL PRIVILEGES ON DATABASE qrdb       TO perimity;
