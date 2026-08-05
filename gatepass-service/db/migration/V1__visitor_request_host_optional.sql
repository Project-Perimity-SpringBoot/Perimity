-- Makes visitor_requests.host_user_id nullable.
--
--   docker exec -i perimity-postgres psql -U perimity -d gatepassdb \
--     < gatepass-service/db/migration/V1__visitor_request_host_optional.sql
--
-- WHY THIS EXISTS
--
-- A visitor now chooses a CAMPUS rather than naming a person, so hostUserId is
-- null on almost every request. The column was NOT NULL, so the first visitor
-- to use the new form got a constraint violation on insert - a 500 with no
-- useful message, on the first screen of the flow.
--
-- Not left to ddl-auto. Hibernate will not drop a NOT NULL from a populated
-- table: it logs a warning and carries on, and the application then looks
-- healthy with the constraint still in place. Same reason qr-service writes its
-- schema changes by hand.
--
-- Safe to re-run. Postgres accepts DROP NOT NULL on a column that is already
-- nullable, and this file does nothing else.

BEGIN;

ALTER TABLE visitor_requests
    ALTER COLUMN host_user_id DROP NOT NULL;

COMMIT;

-- Verify:
--   select column_name, is_nullable from information_schema.columns
--    where table_name = 'visitor_requests' and column_name = 'host_user_id';
-- Expect: host_user_id | YES
