-- Visitor identity and visit-classification fields.
--
--   docker exec -i perimity-postgres psql -U perimity -d gatepassdb \
--     < gatepass-service/db/migration/V2__visitor_request_identity_fields.sql
--
-- ddl-auto=update ADDS the new nullable columns on its own. It will not do the
-- two things below, which is why this file exists:
--
--   1. drop NOT NULL from purpose
--   2. backfill purpose_type and visitor_type before making them NOT NULL
--
-- Run it BEFORE starting a service built from this branch. Hibernate would
-- otherwise try to add two NOT NULL columns to a populated table, fail, log a
-- warning and carry on - leaving a schema that does not match the entity.

BEGIN;

-- 1. New columns. IF NOT EXISTS so this is safe after ddl-auto has already run.
ALTER TABLE visitor_requests ADD COLUMN IF NOT EXISTS purpose_type  VARCHAR(20);
ALTER TABLE visitor_requests ADD COLUMN IF NOT EXISTS visitor_type  VARCHAR(20);
ALTER TABLE visitor_requests ADD COLUMN IF NOT EXISTS gender        VARCHAR(20);
ALTER TABLE visitor_requests ADD COLUMN IF NOT EXISTS date_of_birth DATE;
ALTER TABLE visitor_requests ADD COLUMN IF NOT EXISTS id_type       VARCHAR(20);
ALTER TABLE visitor_requests ADD COLUMN IF NOT EXISTS id_number     VARCHAR(40);
ALTER TABLE visitor_requests ADD COLUMN IF NOT EXISTS id_proof_key  VARCHAR(300);
ALTER TABLE visitor_requests ADD COLUMN IF NOT EXISTS photo_key     VARCHAR(300);

-- 2. purpose becomes optional detail.
--
-- The category is what a queue groups by; the sentence is what an approver
-- reads. Requiring both means asking a visitor to write five words that repeat
-- the dropdown they just used.
ALTER TABLE visitor_requests ALTER COLUMN purpose DROP NOT NULL;

-- 3. Backfill, then constrain.
--
-- Existing rows predate both categories and cannot be classified from their
-- free text without guessing. OTHER is the honest answer: it says "not
-- recorded" rather than inventing a category that filters and counts wrongly
-- for the rest of the table's life.
UPDATE visitor_requests SET purpose_type = 'OTHER' WHERE purpose_type IS NULL;
UPDATE visitor_requests SET visitor_type = 'GUEST' WHERE visitor_type IS NULL;

ALTER TABLE visitor_requests ALTER COLUMN purpose_type SET NOT NULL;
ALTER TABLE visitor_requests ALTER COLUMN visitor_type SET NOT NULL;

-- 4. The queue filters on these two, so they earn an index.
CREATE INDEX IF NOT EXISTS idx_vr_purpose_type ON visitor_requests (purpose_type);
CREATE INDEX IF NOT EXISTS idx_vr_visitor_type ON visitor_requests (visitor_type);

COMMIT;


-- ==========================================================================
--  id_number HOLDS REGULATED DATA. READ BEFORE THIS REACHES A REAL CAMPUS.
-- ==========================================================================
-- With id_type = 'AADHAAR' this column stores a full Aadhaar number. The
-- Aadhaar Act restricts who may hold one and obliges them to protect it, and a
-- campus gate log is not a lawful reason to keep one.
--
-- A gate needs "a guard checked a document and it matched". The last four
-- digits plus the type carries that; a breach of four digits is not a breach of
-- an identity. The column is 40 characters and stores the value whole because
-- the requirement asked for the field - truncating quietly would have hidden
-- the decision rather than made it.
--
-- If the team agrees to mask, this is the change:
--
--   UPDATE visitor_requests
--      SET id_number = right(id_number, 4)
--    WHERE id_number IS NOT NULL AND length(id_number) > 4;
--
-- and store only the last four from then on.
