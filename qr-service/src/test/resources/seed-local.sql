-- Local test data for qr-service.
--
-- Needed because qr-service has no public "create" endpoint by design: a
-- QrRecord only ever appears when the Day 8 RabbitMQ consumer runs. Until
-- then this seed is the only way to get a 200 out of the read endpoints.
--
-- Run:  docker exec -i perimity-postgres psql -U perimity -d qrdb < seed-local.sql
--
-- created_at is nullable = false and normally filled by @CreationTimestamp.
-- Hibernate is not involved in a raw INSERT, so it is supplied explicitly.

BEGIN;

DELETE FROM qr_records WHERE pass_id IN (1, 2);
DELETE FROM generation_jobs WHERE batch_id = 100 OR pass_id IN (1, 2);

-- ---------------------------------------------------------------------------
-- qr_records
-- ---------------------------------------------------------------------------

-- Pass 1, the ACTIVE record. This is what GET /api/qr/1 must return.
INSERT INTO qr_records
    (pass_id, campus_id, token_hash, qr_key, pdf_key, valid_from, valid_to, is_active, created_at)
VALUES
    (1, 1, repeat('a', 64), 'qr/1/current.png', 'pdf/1/current.pdf',
     CURRENT_DATE, CURRENT_DATE + 30, true, NOW());

-- Pass 1 again, an OLD record left behind by a re-issue.
-- This row is the real test: if GET /api/qr/1 ever returns these keys, the
-- "AndActiveTrue" half of the repository lookup is not doing its job and a
-- revoked pass would still resolve.
INSERT INTO qr_records
    (pass_id, campus_id, token_hash, qr_key, pdf_key, valid_from, valid_to,
     is_active, invalidated_at, invalidated_reason, created_at)
VALUES
    (1, 1, repeat('b', 64), 'qr/1/OLD.png', 'pdf/1/OLD.pdf',
     CURRENT_DATE - 60, CURRENT_DATE - 30, false, NOW(), 'Re-issued after loss',
     NOW() - INTERVAL '60 days');

-- Pass 2, a standing DAILY pass with no end date. Confirms a null valid_to
-- serialises cleanly rather than blowing up the response mapping.
INSERT INTO qr_records
    (pass_id, campus_id, token_hash, qr_key, pdf_key, valid_from, valid_to, is_active, created_at)
VALUES
    (2, 1, repeat('c', 64), 'qr/2/current.png', 'pdf/2/current.pdf',
     CURRENT_DATE, NULL, true, NOW());

-- ---------------------------------------------------------------------------
-- generation_jobs : batch 100, five rows, mixed statuses
-- Expected progress -> total 5, done 2, failed 1, processing 1, queued 1,
-- settled = done + failed = 3, so percentComplete 60 and finished false.
-- ---------------------------------------------------------------------------

INSERT INTO generation_jobs
    (pass_id, batch_id, campus_id, status, retry_count, error_message, started_at, completed_at, created_at)
VALUES
    (11, 100, 1, 'DONE',       0, NULL, NOW() - INTERVAL '5 min', NOW() - INTERVAL '4 min', NOW()),
    (12, 100, 1, 'DONE',       0, NULL, NOW() - INTERVAL '5 min', NOW() - INTERVAL '4 min', NOW()),
    (13, 100, 1, 'FAILED',     2, 'S3 upload timed out', NOW() - INTERVAL '5 min', NOW() - INTERVAL '3 min', NOW()),
    (14, 100, 1, 'PROCESSING', 0, NULL, NOW() - INTERVAL '1 min', NULL, NOW()),
    (15, 100, 1, 'QUEUED',     0, NULL, NULL, NULL, NOW());

-- A single (non-batch) job, batch_id null. Confirms a null batchId is fine
-- on the job-status endpoint.
INSERT INTO generation_jobs
    (pass_id, batch_id, campus_id, status, retry_count, created_at)
VALUES
    (1, NULL, 1, 'DONE', 0, NOW());

COMMIT;

-- Note the ids that came back; the job endpoints are keyed on job id, not pass id.
SELECT id, pass_id, batch_id, status FROM generation_jobs ORDER BY id;
SELECT id, pass_id, is_active, valid_to FROM qr_records ORDER BY id;
