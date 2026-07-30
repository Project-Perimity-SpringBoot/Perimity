-- Day 10. Run ONCE against qrdb before starting the service with concurrency
-- above 1.
--
-- Not left to ddl-auto. Hibernate will not add a UNIQUE constraint or a partial
-- index to a populated table reliably - it logs a warning and carries on, which
-- is exactly how Day 9's email_status column silently failed to be created. The
-- application then starts, looks healthy, and the invariant simply is not there.
--
--   docker exec -i perimity-postgres psql -U perimity -d qrdb \
--     < qr-service/db/migration/V2__day10_concurrency.sql

BEGIN;

-- 1. One job row per jobId from gatepass-service.
--
-- Above concurrency 1, two threads handling a redelivery of the same message
-- both find no existing row and both insert. Only the database can arbitrate
-- that; GenerationJobService.claim catches the violation and skips.
--
-- NULLs are unconstrained in Postgres, so pre-Day-8 rows stay valid.
ALTER TABLE generation_jobs
    DROP CONSTRAINT IF EXISTS uk_gj_job_ref;

ALTER TABLE generation_jobs
    ADD CONSTRAINT uk_gj_job_ref UNIQUE (job_ref);

-- 2. At most ONE active QR per pass, enforced by the database.
--
-- The invariant the whole scan path rests on. Application code cannot enforce
-- it under concurrency: SELECT ... FOR UPDATE locks a row that exists, and two
-- concurrent first-ever generations for the same pass have no row to lock.
--
-- A partial index is the right tool - a plain UNIQUE (pass_id) would forbid the
-- retired rows that the audit trail depends on. This says "unique among the
-- live ones", which is precisely the rule.
--
-- If this fails, some pass already has two active QRs. Find them with:
--   SELECT pass_id, count(*) FROM qr_records WHERE is_active
--   GROUP BY pass_id HAVING count(*) > 1;
CREATE UNIQUE INDEX IF NOT EXISTS uk_qr_one_active_per_pass
    ON qr_records (pass_id)
    WHERE is_active;

COMMIT;

-- Verify:
--   \d generation_jobs
--   \d qr_records
