-- Reduces every stored ID number to its last four characters.
--
--   docker exec -i perimity-postgres psql -U perimity -d gatepassdb \
--     < gatepass-service/db/migration/V3__mask_stored_id_numbers.sql
--
-- WHY
--
-- id_number held whole document numbers, and with id_type = 'AADHAAR' that
-- means a full Aadhaar - regulated personal data that a campus gate log has no
-- lawful basis to keep. The same applied to PAN, passport and voter ID: none
-- of them need to be whole for a gate to work.
--
-- A gate asks "did a guard check a document, and did it match". The last four
-- characters plus the type answer that: the visitor reads them off the card in
-- front of them, and a leak of four digits is not a leak of an identity.
--
-- Validation is unaffected. VisitorRequestCreateDto still receives and checks
-- the WHOLE number - Aadhaar's Verhoeff checksum included - and the service
-- discards the rest before the row is written. The number is proven real
-- without being retained.
--
-- IRREVERSIBLE, deliberately. There is no going back to the full values from
-- here, which is the point.
--
-- Safe to re-run: a value already four characters or shorter is left alone.

BEGIN;

UPDATE visitor_requests
   SET id_number = right(id_number, 4)
 WHERE id_number IS NOT NULL
   AND length(id_number) > 4;

COMMIT;

-- Verify - expect no row longer than 4:
--   select id, id_type, id_number, length(id_number)
--     from visitor_requests where id_number is not null;
