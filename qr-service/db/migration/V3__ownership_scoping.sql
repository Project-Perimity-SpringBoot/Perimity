-- PROPOSAL, not yet agreed. See the ownership-scoping question raised with
-- gatepass-service before running this.
--
-- Adds the holder to qr_records so an ownership check on GET /api/qr/{passId}
-- can be answered locally, instead of calling gatepass-service on a read path
-- that every pass view performs.
--
--   docker exec -i perimity-postgres psql -U perimity -d qrdb \
--     < qr-service/db/migration/V3__ownership_scoping.sql

BEGIN;

-- 1. The holder.
--
-- NULLABLE deliberately. Every row already in this table was written before
-- this column existed and has no holder, and nothing in qr-service can derive
-- one - the fact lives in gatepass-service. A NOT NULL column would therefore
-- have to invent a value for existing passes, and the only honest value is
-- absent.
--
-- Not left to ddl-auto for the reason V2 gives: Hibernate will not reliably add
-- a column plus its index to a populated table, it logs a warning and carries
-- on, and the application then starts looking healthy with the invariant simply
-- not there.
ALTER TABLE qr_records
    ADD COLUMN IF NOT EXISTS holder_user_id BIGINT;

-- 2. The read path is "the active QR for this pass, is it yours" - the lookup
-- is still by pass_id, and holder_user_id is only compared afterwards. This
-- index exists for the opposite direction: "every pass belonging to this user",
-- which is what a backfill audit and any future per-holder listing both need.
CREATE INDEX IF NOT EXISTS idx_qr_holder ON qr_records (holder_user_id);

COMMIT;


-- ==========================================================================
--  WHAT THIS MIGRATION DOES NOT DO, AND WHY IT MATTERS
-- ==========================================================================
-- It does not backfill. Existing rows keep holder_user_id = NULL, and
-- QrRecordService.assertMayRead currently FAILS OPEN on a null - any
-- authenticated user can still read those passes.
--
-- So on its own this change closes the gap only for passes issued after it
-- deploys. That is a deliberate choice to avoid an outage, not a complete fix,
-- and it is easy to look at a merged ownership check and believe the hole is
-- shut when for every existing pass it is not.
--
-- Closing it properly needs two things that are gatepass-service's to give:
--
--   1. A backfill - gatepass knows which user holds which pass:
--
--        UPDATE qr_records q
--           SET holder_user_id = g.holder_user_id
--          FROM <gatepass source> g
--         WHERE g.pass_id = q.pass_id
--           AND q.holder_user_id IS NULL;
--
--      The services do not share a database, so this cannot be one statement
--      here. It is an export from gatepass, or an internal endpoint qr-service
--      calls once, or a one-off replay of qr.generate.request.
--
--   2. The flip to fail-closed in assertMayRead, once no NULLs remain:
--
--        SELECT count(*) FROM qr_records WHERE holder_user_id IS NULL;
--
--      Zero before flipping. Non-zero after the flip means those holders can no
--      longer open their own pass.
